package net.timafe.triptale.wordpress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.timafe.triptale.config.AppSettings;

import java.io.IOException;
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

/**
 * Create/update/get a WordPress.com post via {@code public-api.wordpress.com/wp/v2} (todo 60,
 * Piece 3) — the send rules (create vs. update vs. conflict) live in the caller
 * ({@code ui.dialog.ExportDiaryDialog}), this class only wraps the three REST calls.
 */
public final class WordPressApiClient {

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

    /** Creates a new draft. */
    public PostResult createDraft(AppSettings.WordPress settings, String title, String contentHtml) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("title", title);
        form.put("content", contentHtml);
        form.put("status", "draft");
        return send(postsUrl(settings), settings.getAccessToken(), form);
    }

    /**
     * Updates an existing post's title/content. Deliberately never sends {@code status} — per the
     * send rules, publishing only ever happens in WordPress, never from TripTale, so an update
     * must not accidentally flip a published post back to (or away from) draft.
     */
    public PostResult updatePost(AppSettings.WordPress settings, String id, String title, String contentHtml) {
        Map<String, String> form = new LinkedHashMap<>();
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

    private PostResult send(String url, String accessToken, Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(FormCodec.encode(form)))
                .build();
        HttpResponse<String> response = execute(request);
        checkOk(response);
        return parse(response.body());
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
            throw new WordPressException("WordPress API call failed: HTTP " + response.statusCode()
                    + " – " + response.body());
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
