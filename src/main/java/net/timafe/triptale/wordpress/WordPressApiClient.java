package net.timafe.triptale.wordpress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.timafe.triptale.config.AppSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * Create/update/get a WordPress.com post via {@code public-api.wordpress.com/wp/v2} (todo 60,
 * Piece 3) — the send rules (create vs. update vs. conflict) live in the caller
 * ({@code ui.dialog.ExportDiaryDialog}), this class only wraps the three REST calls.
 */
public final class WordPressApiClient {

    private static final Logger log = LoggerFactory.getLogger(WordPressApiClient.class);
    private static final String API_BASE = "https://public-api.wordpress.com/wp/v2/sites/";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /** A post as WordPress reports it: {@code modified} is {@code modified_gmt} parsed as an Instant. */
    public record PostResult(String id, String status, Instant modified, String link) {}

    /** The post doesn't exist (anymore) — 404, e.g. deleted or permanently trashed on the WordPress side. */
    public static final class NotFoundException extends WordPressException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Creates a new draft. {@code tags} (names, may be empty) are sent here only: on update WordPress
     * would replace the post's whole tag set, wiping what was edited there.
     */
    public PostResult createDraft(AppSettings.WordPress settings, String title, String contentHtml, List<String> tags) {
        Map<String, String> form = new LinkedHashMap<>();
        if (tags != null && !tags.isEmpty()) form.put("tags", tagIds(settings, tags));
        form.put("title", title);
        form.put("content", contentHtml);
        form.put("status", "draft");
        return send(postsUrl(settings), settings.getAccessToken(), form);
    }

    /**
     * Updates an existing post's title/content. Deliberately never sends {@code status} — per the
     * send rules, publishing only ever happens in WordPress, never from TripTale, so an update
     * must not accidentally flip a published post back to (or away from) draft.
     *
     * <p>{@code tags} is null or empty to leave the post's tags untouched; otherwise the list
     * <em>replaces</em> all tags on the post (WordPress doesn't merge), so the caller must opt in.
     */
    public PostResult updatePost(AppSettings.WordPress settings, String id, String title, String contentHtml,
                                 List<String> tags) {
        Map<String, String> form = new LinkedHashMap<>();
        if (tags != null && !tags.isEmpty()) form.put("tags", tagIds(settings, tags));
        form.put("title", title);
        form.put("content", contentHtml);
        return send(postsUrl(settings) + "/" + id, settings.getAccessToken(), form);
    }

    /** @throws NotFoundException if {@code id} is gone (404) — the caller offers creating a new draft instead. */
    public PostResult getPost(AppSettings.WordPress settings, String id) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(postsUrl(settings) + "/" + id))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + settings.getAccessToken())
                .GET()
                .build();
        HttpResponse<String> response = execute(request);
        if (response.statusCode() == 404) {
            throw new NotFoundException("WordPress post " + id + " was not found (deleted or trashed?)");
        }
        checkOk(response);
        return parse(response.body());
    }

    /**
     * The wp/v2 posts endpoint wants tag term IDs, not names: looks each name up (exact match,
     * ignoring case) and creates the missing ones. Returns the IDs comma-separated, which
     * WordPress accepts for array parameters.
     */
    private String tagIds(AppSettings.WordPress settings, List<String> names) {
        List<String> ids = new ArrayList<>();
        for (String name : names) ids.add(tagId(settings, name));
        return String.join(",", ids);
    }

    private String tagId(AppSettings.WordPress settings, String name) {
        String url = API_BASE + settings.getSite() + "/tags";
        HttpResponse<String> found = execute(HttpRequest.newBuilder()
                .uri(URI.create(url + "?per_page=100&search=" + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + settings.getAccessToken())
                .GET().build());
        checkOk(found);
        try {
            for (JsonNode tag : JSON.readTree(found.body())) {
                if (name.equalsIgnoreCase(tag.path("name").asText(""))) {
                    log.info("WordPress: tag '{}' reuses existing '{}' (id {})", name, tag.path("name").asText(), tag.path("id").asText());
                    return tag.path("id").asText();
                }
            }
        } catch (IOException e) {
            throw new WordPressException("Unexpected response from WordPress.com: " + e.getMessage(), e);
        }
        HttpResponse<String> created = execute(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + settings.getAccessToken())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(FormCodec.encode(Map.of("name", name))))
                .build());
        if (created.statusCode() == 400) {
            // term_exists (e.g. name differs only by HTML escaping): WordPress names the existing term's id
            try {
                String existing = JSON.readTree(created.body()).path("data").path("term_id").asText("");
                if (!existing.isEmpty()) return existing;
            } catch (IOException ignored) {
                // fall through to the normal error
            }
        }
        checkOk(created);
        try {
            String id = JSON.readTree(created.body()).path("id").asText();
            log.info("WordPress: created tag '{}' (id {})", name, id);
            return id;
        } catch (IOException e) {
            throw new WordPressException("Unexpected response from WordPress.com: " + e.getMessage(), e);
        }
    }

    private PostResult send(String url, String accessToken, Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(FormCodec.encode(form)))
                .build();
        log.info("WordPress: POST {} (title='{}', content={} chars, tags={}, status={})", url, form.get("title"),
                form.getOrDefault("content", "").length(), form.getOrDefault("tags", "-"), form.getOrDefault("status", "-"));
        HttpResponse<String> response = execute(request);
        checkOk(response);
        PostResult result = parse(response.body());
        log.info("WordPress: post {} is '{}', modified {}, {}", result.id(), result.status(), result.modified(), result.link());
        return result;
    }

    private HttpResponse<String> execute(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new WordPressException("Could not reach WordPress.com: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WordPressException("WordPress request interrupted", e);
        }
    }

    private static void checkOk(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            log.warn("WordPress API call failed: HTTP {} {} – {}", response.statusCode(),
                    response.request().uri(), response.body());
            String detail = response.body();
            try {
                JsonNode json = JSON.readTree(detail);
                if (json.hasNonNull("message")) {
                    detail = json.path("code").asText("") + ": " + json.path("message").asText();
                }
            } catch (IOException ignored) {
                // not JSON: keep the raw body
            }
            throw new WordPressException("WordPress API call failed: HTTP " + response.statusCode() + " – " + detail);
        }
    }

    private static PostResult parse(String body) {
        try {
            JsonNode node = JSON.readTree(body);
            return new PostResult(
                    node.path("id").asText(""),
                    node.path("status").asText(""),
                    parseModifiedGmt(node.path("modified_gmt").asText("")),
                    node.path("link").asText(""));
        } catch (IOException e) {
            throw new WordPressException("Unexpected response from WordPress.com: " + e.getMessage(), e);
        }
    }

    /** {@code modified_gmt} is UTC but usually has no zone designator, e.g. {@code 2026-03-23T14:00:00}. */
    private static Instant parseModifiedGmt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC);
        }
    }

    private static String postsUrl(AppSettings.WordPress settings) {
        return API_BASE + settings.getSite() + "/posts";
    }
}
