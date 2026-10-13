package net.timafe.triptale.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;

/**
 * Reverse geocoding (todo 51, ADR-0003): coordinates in, a place name like {@code "Soest, DE"}
 * out, from the public Nominatim instance. Follows its usage policy: an identifying
 * User-Agent and at most one request per second, the second caller simply waits.
 *
 * <p>Failures are thrown as {@link LookupException} with a short message meant for a tooltip.
 * No JavaFX imports (package boundary rule).
 */
public class PlaceLookup {

    private static final Logger log = LoggerFactory.getLogger(PlaceLookup.class);

    /** A constant, not a setting: another provider needs a different response mapping anyway. */
    static final URI NOMINATIM = URI.create("https://nominatim.openstreetmap.org");
    static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(1);
    private static final String[] NAME_KEYS = {"city", "town", "village"};

    /** A looked-up place; {@code kind} is the address key the name came from (city, town, …). */
    public record Place(String name, String countryCode, String kind) {
        /** {@code "Soest, DE"}, or just the name without a country code. */
        public String label() {
            return countryCode == null ? name : name + ", " + countryCode;
        }
    }

    /** A failed lookup; the message is short and human, e.g. "Timed out after 5 s". */
    public static class LookupException extends RuntimeException {
        public LookupException(String message) { super(message); }
    }

    private final URI baseUrl;
    private final String userAgent;
    private final Duration minInterval;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private long lastRequestNanos;
    private boolean anyRequest;

    public PlaceLookup(String appVersion) {
        this(NOMINATIM, appVersion, MIN_INTERVAL);
    }

    PlaceLookup(URI baseUrl, String appVersion, Duration minInterval) {
        this.baseUrl = baseUrl;
        this.userAgent = "TripTale/" + (appVersion == null || appVersion.isBlank() ? "dev" : appVersion)
                + " (+https://github.com/tillkuhn/triptale)";
        this.minInterval = minInterval;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** Blocks (network, rate limit): call off the FX thread. */
    public Place lookup(double lat, double lon) {
        URI uri = baseUrl.resolve(String.format(Locale.ROOT,
                "/reverse?lat=%.6f&lon=%.6f&format=jsonv2&zoom=10&accept-language=en", lat, lon));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response;
        long started;
        try {
            awaitRateLimit();
            log.debug("Place lookup GET {}", uri);
            started = System.nanoTime();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            log.debug("Place lookup timed out: {}", uri);
            throw new LookupException("Timed out after " + TIMEOUT.toSeconds() + " s");
        } catch (IOException e) {
            log.debug("Place lookup failed: {}", uri, e);
            throw new LookupException("Network error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LookupException("Interrupted");
        }
        long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
        log.debug("Place lookup HTTP {} in {} ms: {}", response.statusCode(), ms, response.body());
        if (response.statusCode() != 200) {
            throw new LookupException("OpenStreetMap answered HTTP " + response.statusCode());
        }
        try {
            Place place = parse(response.body());
            log.debug("Place lookup result: {} ({})", place.label(), place.kind());
            return place;
        } catch (LookupException e) {
            log.debug("Place lookup result: none ({})", e.getMessage());
            throw e;
        }
    }

    /** Maps a Nominatim {@code jsonv2} reverse response to a place; package-private for tests. */
    Place parse(String body) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (IOException e) {
            throw new LookupException("Unreadable answer from OpenStreetMap");
        }
        if (root == null || root.hasNonNull("error")) throw new LookupException("No place found here");
        JsonNode address = root.path("address");
        String name = null;
        String kind = null;
        for (String key : NAME_KEYS) {
            String value = text(address, key);
            if (value != null) {
                name = value;
                kind = key;
                break;
            }
        }
        if (name == null) {
            name = text(root, "name");
            kind = "place";
        }
        if (name == null) throw new LookupException("No place found here");
        String cc = text(address, "country_code");
        return new Place(name, cc == null ? null : cc.toUpperCase(Locale.ROOT), kind);
    }

    private synchronized void awaitRateLimit() throws InterruptedException {
        if (anyRequest) {
            long waitNanos = minInterval.toNanos() - (System.nanoTime() - lastRequestNanos);
            if (waitNanos > 0) Thread.sleep(Duration.ofNanos(waitNanos));
        }
        anyRequest = true;
        lastRequestNanos = System.nanoTime();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText().trim() : null;
    }
}
