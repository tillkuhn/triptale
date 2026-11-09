package net.timafe.triptale.weblink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives a display name for a weblink (todo 91): fetches the page and takes {@code og:title},
 * then {@code <title>}. Cleanup is trim only: whitespace collapsed, entities decoded, length
 * capped. Only the first {@value #MAX_BYTES} bytes are read.
 *
 * <p>Failures are thrown as {@link LookupException} with a short message meant for a tooltip.
 * No JavaFX imports (package boundary rule).
 */
public class PageTitleLookup {

    private static final Logger log = LoggerFactory.getLogger(PageTitleLookup.class);

    static final Duration TIMEOUT = Duration.ofSeconds(8);
    static final int MAX_BYTES = 64 * 1024;
    static final int MAX_LENGTH = 60;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/17.0 Safari/605.1.15";

    private static final Pattern META_TAG = Pattern.compile("<meta\\s[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");
    private static final Pattern TITLE_TAG = Pattern.compile(
            "<title[^>]*>(.*?)</title\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HEADER_CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([-\\w.:]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_CHARSET = Pattern.compile(
            "<meta[^>]+charset\\s*=\\s*[\"']?([-\\w.:]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENTITY = Pattern.compile("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);");
    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"), Map.entry("quot", "\""),
            Map.entry("apos", "'"), Map.entry("nbsp", " "), Map.entry("ndash", "–"), Map.entry("mdash", "—"),
            Map.entry("hellip", "…"), Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"),
            Map.entry("ldquo", "“"), Map.entry("rdquo", "”"), Map.entry("bull", "•"),
            Map.entry("middot", "·"), Map.entry("copy", "©"), Map.entry("reg", "®"),
            Map.entry("euro", "€"), Map.entry("auml", "ä"), Map.entry("ouml", "ö"), Map.entry("uuml", "ü"),
            Map.entry("Auml", "Ä"), Map.entry("Ouml", "Ö"), Map.entry("Uuml", "Ü"), Map.entry("szlig", "ß"),
            Map.entry("eacute", "é"), Map.entry("egrave", "è"), Map.entry("aacute", "á"),
            Map.entry("agrave", "à"), Map.entry("ccedil", "ç"), Map.entry("ntilde", "ñ"));

    /** A failed lookup; the message is short and human, e.g. "Timed out after 8 s". */
    public static class LookupException extends RuntimeException {
        public LookupException(String message) { super(message); }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Fetches {@code url} and returns its derived name. Blocks (network): call off the FX thread. */
    public String lookup(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new LookupException("Not a valid URL");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
                .header("Accept-Language", "en,de;q=0.8")
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            log.debug("Weblink title GET {}", uri);
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    log.debug("Weblink title HTTP {}: {}", status, uri);
                    throw new LookupException("Page answered HTTP " + status);
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (!contentType.isBlank() && !isHtml(contentType)) {
                    log.debug("Weblink title: no title found, content type is '{}' (not HTML): {}", contentType, uri);
                    throw new LookupException("No title found");
                }
                byte[] body = in.readNBytes(MAX_BYTES);
                String title = extractTitle(body, contentType)
                        .orElseThrow(() -> {
                            log.debug("Weblink title: no og:title or <title> in {} bytes: {}", body.length, uri);
                            return new LookupException("No title found");
                        });
                log.debug("Weblink title result: '{}' ({})", title, uri);
                return title;
            }
        } catch (HttpTimeoutException e) {
            log.debug("Weblink title timed out: {}", uri);
            throw new LookupException("Timed out after " + TIMEOUT.toSeconds() + " s");
        } catch (IOException e) {
            log.debug("Weblink title failed: {}", uri, e);
            throw new LookupException("Network error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LookupException("Interrupted");
        } catch (IllegalArgumentException e) {
            throw new LookupException("Not a valid URL");
        }
    }

    private static boolean isHtml(String contentType) {
        String type = contentType.toLowerCase(Locale.ROOT);
        return type.contains("text/html") || type.contains("application/xhtml");
    }

    /** Decodes the bytes (header charset, then {@code <meta charset>}, then UTF-8) and extracts the title; package-private for tests. */
    static Optional<String> extractTitle(byte[] body, String contentType) {
        String html = new String(body, charsetOf(body, contentType));
        return extractTitle(html);
    }

    static Optional<String> extractTitle(String html) {
        String raw = null;
        Matcher metas = META_TAG.matcher(html);
        while (metas.find()) {
            Map<String, String> attrs = attributes(metas.group());
            String key = attrs.getOrDefault("property", attrs.getOrDefault("name", ""));
            if (key.equalsIgnoreCase("og:title") && attrs.containsKey("content")) {
                raw = attrs.get("content");
                if (!clean(raw).isEmpty()) break;
                raw = null;
            }
        }
        if (raw == null) {
            Matcher title = TITLE_TAG.matcher(html);
            if (title.find()) raw = title.group(1);
        }
        if (raw == null) return Optional.empty();
        String cleaned = clean(raw);
        return cleaned.isEmpty() ? Optional.empty() : Optional.of(cleaned);
    }

    private static Charset charsetOf(byte[] body, String contentType) {
        Charset header = parseCharset(HEADER_CHARSET, contentType);
        if (header != null) return header;
        Charset meta = parseCharset(META_CHARSET, new String(body, StandardCharsets.ISO_8859_1));
        return meta != null ? meta : StandardCharsets.UTF_8;
    }

    private static Charset parseCharset(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text == null ? "" : text);
        if (!m.find()) return null;
        try {
            return Charset.forName(m.group(1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, String> attributes(String tag) {
        Map<String, String> out = new HashMap<>();
        Matcher m = ATTRIBUTE.matcher(tag);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
            out.putIfAbsent(m.group(1).toLowerCase(Locale.ROOT), value);
        }
        return out;
    }

    /** Decodes entities, collapses whitespace, trims, caps the length with an ellipsis. */
    static String clean(String raw) {
        String text = decodeEntities(raw).replaceAll("\\s+", " ").trim();
        if (text.length() > MAX_LENGTH) text = text.substring(0, MAX_LENGTH - 1).stripTrailing() + "…";
        return text;
    }

    static String decodeEntities(String text) {
        Matcher m = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String replacement = null;
            if (name.charAt(0) == '#') {
                try {
                    int cp = name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X')
                            ? Integer.parseInt(name.substring(2), 16)
                            : Integer.parseInt(name.substring(1));
                    if (Character.isValidCodePoint(cp) && cp != 0) replacement = new String(Character.toChars(cp));
                } catch (NumberFormatException ignored) {
                    // leave as-is
                }
            } else {
                replacement = NAMED.get(name);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement != null ? replacement : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }
}
