package net.timafe.triptale.wordpress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import net.timafe.triptale.config.AppSettings;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * WordPress.com OAuth2 Authorization Code Flow (todo 60, Pieces 1) over a loopback HTTP
 * listener, plus the token exchange. No JavaFX — the caller (a dialog) runs {@link #connect}
 * on a background thread and opens the authorize URL via {@code ui.BrowserLauncher}.
 *
 * <p>The redirect URI is fixed to {@value #REDIRECT_URI} and must exactly match the one
 * registered for the app at developer.wordpress.com/apps/ (WordPress.com's OAuth2 docs: even a
 * missing trailing slash breaks the match) — this is why the port isn't configurable.
 */
public final class WordPressAuthClient {

    private static final String AUTHORIZE_URL = "https://public-api.wordpress.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://public-api.wordpress.com/oauth2/token";
    private static final int PORT = 8765;
    private static final String REDIRECT_URI = "http://127.0.0.1:" + PORT + "/callback";
    // Verified 2026-10-14: the wp/v2 REST namespace (used for posts create/update/get, todo 60)
    // rejects a "posts"-scoped token with HTTP 403 "Required scope: global" — unlike the older
    // v1.1 API, it needs the broader "global" scope.
    private static final String SCOPE = "global";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOKEN_REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The access token plus the blog it was issued for, as returned by the token endpoint. */
    public record TokenResult(String accessToken, String blogId, String blogUrl, String tokenType) {}

    /**
     * Runs the full Authorization Code Flow: starts a loopback listener, hands the authorize URL
     * to {@code openUrl} (expected to open the system browser), blocks until the redirect lands
     * or {@code timeout} elapses, then exchanges the code for a token. Blocking — call off the
     * FX thread.
     */
    public TokenResult connect(AppSettings.WordPress settings, Consumer<String> openUrl, Duration timeout) {
        String state = randomState();
        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server = startCallbackServer(state, codeFuture);
        try {
            openUrl.accept(authorizeUrl(settings, state));
            String code = awaitCode(codeFuture, timeout);
            return exchangeToken(settings, code);
        } finally {
            server.stop(0);
        }
    }

    private String authorizeUrl(AppSettings.WordPress settings, String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", settings.getClientId());
        params.put("redirect_uri", REDIRECT_URI);
        params.put("response_type", "code");
        params.put("scope", SCOPE);
        params.put("blog", settings.getSite());
        params.put("state", state);
        return AUTHORIZE_URL + "?" + FormCodec.encode(params);
    }

    private HttpServer startCallbackServer(String expectedState, CompletableFuture<String> codeFuture) {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        } catch (IOException e) {
            throw new WordPressException("Could not open local port " + PORT + " for the WordPress login redirect: "
                    + e.getMessage(), e);
        }
        server.createContext("/callback", exchange -> {
            try {
                Map<String, String> query = FormCodec.decode(exchange.getRequestURI().getRawQuery());
                String message;
                if (query.containsKey("error")) {
                    codeFuture.completeExceptionally(
                            new WordPressException("WordPress authorization was denied (" + query.get("error") + ")"));
                    message = "Authorization was declined. You can close this window and return to TripTale.";
                } else if (!expectedState.equals(query.get("state"))) {
                    codeFuture.completeExceptionally(
                            new WordPressException("Authorization response failed a security check (state mismatch)"));
                    message = "Security check failed. Close this window and try again in TripTale.";
                } else if (query.get("code") == null || query.get("code").isBlank()) {
                    codeFuture.completeExceptionally(new WordPressException("No authorization code in the redirect"));
                    message = "No authorization code received. Close this window and try again in TripTale.";
                } else {
                    codeFuture.complete(query.get("code"));
                    message = "TripTale is connected to WordPress. You can close this window.";
                }
                byte[] html = ("<html><body style=\"font-family:sans-serif;padding:2em\">" + message + "</body></html>")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, html.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(html);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private String awaitCode(CompletableFuture<String> codeFuture, Duration timeout) {
        try {
            return codeFuture.get(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new WordPressException("Timed out waiting for the WordPress login — check the browser window");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof WordPressException wpe ? wpe
                    : new WordPressException("WordPress authorization failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WordPressException("Interrupted while waiting for the WordPress login", e);
        }
    }

    private TokenResult exchangeToken(AppSettings.WordPress settings, String code) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", settings.getClientId());
        form.put("client_secret", settings.getClientSecret());
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", REDIRECT_URI);

        HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(TOKEN_REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(FormCodec.encode(form)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new WordPressException("Could not reach WordPress.com for the token exchange: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WordPressException("Token exchange interrupted", e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new WordPressException("WordPress token exchange failed: HTTP " + response.statusCode()
                    + " – " + response.body());
        }
        try {
            JsonNode node = JSON.readTree(response.body());
            return new TokenResult(
                    node.path("access_token").asText(""),
                    node.path("blog_id").asText(""),
                    node.path("blog_url").asText(""),
                    node.path("token_type").asText(""));
        } catch (IOException e) {
            throw new WordPressException("Unexpected response from WordPress.com: " + e.getMessage(), e);
        }
    }

    private static String randomState() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
