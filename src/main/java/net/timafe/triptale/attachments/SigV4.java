package net.timafe.triptale.attachments;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.SortedMap;

/**
 * AWS Signature Version 4 for S3 requests, header-based (no presigned URLs). See
 * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html">
 * Signature Calculations for the Authorization Header</a>.
 */
final class SigV4 {

    static final String ALGORITHM = "AWS4-HMAC-SHA256";
    static final String SERVICE = "s3";
    static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final HexFormat HEX = HexFormat.of();

    private SigV4() {}

    /**
     * Builds the {@code Authorization} header value.
     *
     * @param canonicalPath  URI-encoded path, e.g. {@code /attachments/a%20b.jpg}
     * @param canonicalQuery encoded, sorted query string (see {@link #canonicalQuery}), or ""
     * @param headers        every header to sign, lower-case names, including {@code host},
     *                       {@code x-amz-date} and {@code x-amz-content-sha256}
     * @param amzDate        the {@code x-amz-date} value, {@code yyyyMMdd'T'HHmmss'Z'}
     */
    static String authorization(String method, String canonicalPath, String canonicalQuery,
                                SortedMap<String, String> headers, String payloadHash,
                                String accessKeyId, String secretAccessKey, String region,
                                String amzDate) {
        StringBuilder canonicalHeaders = new StringBuilder();
        headers.forEach((k, v) -> canonicalHeaders.append(k).append(':').append(v.trim()).append('\n'));
        String signedHeaders = String.join(";", headers.keySet());
        String canonicalRequest = String.join("\n", method, canonicalPath, canonicalQuery,
                canonicalHeaders.toString(), signedHeaders, payloadHash);

        String date = amzDate.substring(0, 8);
        String scope = date + "/" + region + "/" + SERVICE + "/aws4_request";
        String stringToSign = String.join("\n", ALGORITHM, amzDate, scope, sha256Hex(canonicalRequest));

        byte[] key = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
        key = hmac(key, region);
        key = hmac(key, SERVICE);
        key = hmac(key, "aws4_request");
        String signature = HEX.formatHex(hmac(key, stringToSign));

        return ALGORITHM + " Credential=" + accessKeyId + "/" + scope
                + ",SignedHeaders=" + signedHeaders + ",Signature=" + signature;
    }

    /** Query string with encoded names and values, sorted by name (params must be pre-sorted). */
    static String canonicalQuery(SortedMap<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(uriEncode(e.getKey(), true)).append('=').append(uriEncode(e.getValue(), true));
        }
        return sb.toString();
    }

    /**
     * Percent-encodes everything except the unreserved characters {@code A-Z a-z 0-9 - _ . ~};
     * {@code /} too unless {@code encodeSlash} is false (object key paths).
     */
    static String uriEncode(String s, boolean encodeSlash) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~' || (c == '/' && !encodeSlash)) {
                sb.append(c);
            } else {
                sb.append('%').append(HEX.withUpperCase().toHexDigits(b));
            }
        }
        return sb.toString();
    }

    static String sha256Hex(String s) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
