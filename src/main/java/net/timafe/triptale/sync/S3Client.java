package net.timafe.triptale.sync;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.BucketUrl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Minimal AWS S3 client over {@code java.net.http} with our own {@link SigV4} signing — just
 * {@code ListObjectsV2}, {@code PutObject} and {@code GetObject} (todo 33, D2; 33b). Uses virtual-hosted URLs
 * ({@code https://<bucket>.s3.<region>.amazonaws.com}), so bucket names with dots aren't supported.
 */
public final class S3Client implements ObjectStore {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration PUT_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration GET_TIMEOUT = Duration.ofMinutes(10);

    private final String bucket;
    private final String region;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String host;
    private final HttpClient http;

    public S3Client(String bucket, String region, String accessKeyId, String secretAccessKey) {
        this.bucket = bucket;
        this.region = region;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.host = bucket + ".s3." + region + ".amazonaws.com";
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static S3Client from(AppSettings.Attachments settings) {
        return new S3Client(BucketUrl.parse(settings.getBucketUrl()).bucket(), settings.getRegion(),
                settings.getAccessKeyId(), settings.getSecretAccessKey());
    }

    @Override
    public Map<String, ObjectInfo> list(String keyPrefix) {
        Map<String, ObjectInfo> objects = new HashMap<>();
        String token = null;
        do {
            SortedMap<String, String> params = new TreeMap<>();
            params.put("list-type", "2");
            params.put("prefix", keyPrefix);
            if (token != null) params.put("continuation-token", token);
            HttpResponse<byte[]> resp = send("GET", "/", SigV4.canonicalQuery(params), Map.of(),
                    SigV4.EMPTY_SHA256, HttpRequest.BodyPublishers.noBody(), LIST_TIMEOUT,
                    HttpResponse.BodyHandlers.ofByteArray());
            checkStatus("GET", "/", resp.statusCode(), resp.body());
            Document doc = parseXml(resp.body());
            NodeList contents = doc.getElementsByTagName("Contents");
            for (int i = 0; i < contents.getLength(); i++) {
                Element c = (Element) contents.item(i);
                objects.put(text(c, "Key"), new ObjectInfo(
                        Long.parseLong(text(c, "Size")), text(c, "ETag").replace("\"", "")));
            }
            token = "true".equals(text(doc.getDocumentElement(), "IsTruncated"))
                    ? text(doc.getDocumentElement(), "NextContinuationToken") : null;
        } while (token != null);
        return objects;
    }

    @Override
    public void put(String key, Path file, byte[] md5) {
        HttpRequest.BodyPublisher body;
        String contentType;
        try {
            body = HttpRequest.BodyPublishers.ofFile(file);
            contentType = Files.probeContentType(file);
        } catch (FileNotFoundException e) {
            throw new SyncException("File vanished before upload: " + file, e);
        } catch (IOException e) {
            throw new SyncException("Could not read " + file, e);
        }
        Map<String, String> headers = Map.of(
                "content-md5", Base64.getEncoder().encodeToString(md5),
                "content-type", contentType == null ? "application/octet-stream" : contentType);
        String path = "/" + SigV4.uriEncode(key, false);
        HttpResponse<byte[]> resp = send("PUT", path, "", headers, SigV4.UNSIGNED_PAYLOAD, body, PUT_TIMEOUT,
                HttpResponse.BodyHandlers.ofByteArray());
        checkStatus("PUT", path, resp.statusCode(), resp.body());
    }

    /**
     * Streams the object to a hidden {@code .part} file next to {@code target} (so memory stays
     * flat for large photos), then moves it into place — an aborted download never leaves a
     * truncated attachment. Hidden files are skipped by push, so a leftover part file is harmless.
     */
    @Override
    public void get(String key, Path target) {
        String path = "/" + SigV4.uriEncode(key, false);
        Path part = target.resolveSibling("." + target.getFileName() + ".part");
        try {
            HttpResponse<Path> resp = send("GET", path, "", Map.of(), SigV4.EMPTY_SHA256,
                    HttpRequest.BodyPublishers.noBody(), GET_TIMEOUT, HttpResponse.BodyHandlers.ofFile(part));
            if (resp.statusCode() / 100 != 2) {
                byte[] errorBody = Files.readAllBytes(part);
                checkStatus("GET", path, resp.statusCode(), errorBody);
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new SyncException("Could not write " + target, e);
        } finally {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // best effort; push skips hidden files anyway
            }
        }
    }

    private <T> HttpResponse<T> send(String method, String path, String query, Map<String, String> extraHeaders,
                                     String payloadHash, HttpRequest.BodyPublisher body, Duration timeout,
                                     HttpResponse.BodyHandler<T> handler) {
        String amzDate = ZonedDateTime.now(ZoneOffset.UTC).format(AMZ_DATE);
        SortedMap<String, String> headers = new TreeMap<>(extraHeaders);
        headers.put("host", host);
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", amzDate);
        String authorization = SigV4.authorization(method, path, query, headers, payloadHash,
                accessKeyId, secretAccessKey, region, amzDate);

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host + path + (query.isEmpty() ? "" : "?" + query)))
                .timeout(timeout)
                .method(method, body)
                .header("Authorization", authorization);
        // java.net.http derives Host from the URI and refuses to set it explicitly
        headers.forEach((k, v) -> { if (!k.equals("host")) req.header(k, v); });

        try {
            return http.send(req.build(), handler);
        } catch (IOException e) {
            throw new SyncException("S3 " + method + " to " + bucket + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyncException("S3 " + method + " interrupted", e);
        }
    }

    private static void checkStatus(String method, String path, int status, byte[] body) {
        if (status / 100 != 2) {
            throw new SyncException("S3 " + method + " " + path + " → HTTP " + status + errorDetail(body));
        }
    }

    /** {@code ": <Code> – <Message>"} from an S3 XML error body, or "" if there's none. */
    private static String errorDetail(byte[] body) {
        if (body == null || body.length == 0) return "";
        try {
            Element root = parseXml(body).getDocumentElement();
            return ": " + text(root, "Code") + " – " + text(root, "Message");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static Document parseXml(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new SyncException("Unexpected S3 response: " + e.getMessage(), e);
        }
    }

    /** Text of the first direct-or-nested child element with that tag, or "" if absent. */
    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }
}
