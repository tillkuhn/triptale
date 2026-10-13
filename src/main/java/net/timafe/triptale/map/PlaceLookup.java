package net.timafe.triptale.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Place lookups against the public Nominatim instance (ADR-0003): reverse (todo 51,
 * coordinates in, a place name like {@code "Soest, DE"} out) and forward (todo 59, a place
 * name in, its centre point out). Follows the usage policy: an identifying User-Agent and at
 * most one request per second for both directions together, the second caller simply waits.
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
    /** A trailing {@code ", XX"} two-letter country code, as in {@code "Bangkok, TH"}. */
    private static final Pattern TRAILING_COUNTRY = Pattern.compile("^(.*?)\\s*,\\s*([A-Za-z]{2})$");

    /** A looked-up place; {@code kind} is the address key the name came from (city, town, …). */
    public record Place(String name, String countryCode, String kind) {
        /** {@code "Soest, DE"}, or just the name without a country code. */
        public String label() {
            return countryCode == null ? name : name + ", " + countryCode;
        }
    }

    /** A forward lookup's hit: the place's centre point and the place itself. */
    public record Match(double lat, double lon, Place place) {}

    /** A search text split into the query and an optional lowercase country code. */
    record Query(String text, String countryCode) {}

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

    /** Reverse lookup. Blocks (network, rate limit): call off the FX thread. */
    public Place lookup(double lat, double lon) {
        String body = get(String.format(Locale.ROOT,
                "/reverse?lat=%.6f&lon=%.6f&format=jsonv2&zoom=10&accept-language=en", lat, lon));
        try {
            Place place = parse(body);
            log.debug("Place lookup result: {} ({})", place.label(), place.kind());
            return place;
        } catch (LookupException e) {
            log.debug("Place lookup result: none ({})", e.getMessage());
            throw e;
        }
    }

    /**
     * Forward lookup: the best match for a place name, optionally ending in a two-letter
     * country code ({@code "Bangkok, TH"}). Blocks (network, rate limit): call off the FX thread.
     */
    public Match search(String text) {
        Query query = splitQuery(text);
        if (query.text().isBlank()) throw new LookupException("Enter a place name first");
        String path = "/search?q=" + URLEncoder.encode(query.text(), StandardCharsets.UTF_8)
                + (query.countryCode() == null ? "" : "&countrycodes=" + query.countryCode())
                + "&format=jsonv2&limit=1&addressdetails=1&accept-language=en";
        String body = get(path);
        try {
            Match match = parseSearch(body);
            log.debug("Place search result: {} at {}, {} ({})",
                    match.place().label(), match.lat(), match.lon(), match.place().kind());
            return match;
        } catch (LookupException e) {
            log.debug("Place search result: none ({})", e.getMessage());
            throw e;
        }
    }

    /** Splits off a trailing {@code ", XX"} country code; package-private for tests. */
    static Query splitQuery(String text) {
        String trimmed = text == null ? "" : text.trim();
        Matcher m = TRAILING_COUNTRY.matcher(trimmed);
        if (m.matches()) return new Query(m.group(1), m.group(2).toLowerCase(Locale.ROOT));
        return new Query(trimmed, null);
    }

    /**
     * The place text to keep after a forward lookup: the typed name as it is, with the hit's
     * country code as an uppercase {@code ", XX"} suffix ({@code "Krung Thep Aphiwat"} →
     * {@code "Krung Thep Aphiwat, TH"}), so a wrong country catches the eye. A typed code is
     * replaced, which normalizes {@code "ulm,de"} to {@code "ulm, DE"}; a hit without a country
     * keeps the typed code, uppercased.
     */
    public static String labelFor(String typed, Place hit) {
        Query query = splitQuery(typed);
        String cc = hit != null && hit.countryCode() != null ? hit.countryCode() : query.countryCode();
        return cc == null ? query.text() : query.text() + ", " + cc.toUpperCase(Locale.ROOT);
    }

    /** GETs a path below the base URL and returns the body of a 200 answer. */
    private String get(String pathAndQuery) {
        URI uri = baseUrl.resolve(pathAndQuery);
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
        return response.body();
    }

    /** Maps a Nominatim {@code jsonv2} reverse response to a place; package-private for tests. */
    Place parse(String body) {
        JsonNode root = readTree(body);
        if (root == null || root.hasNonNull("error")) throw new LookupException("No place found here");
        return placeFrom(root, "No place found here");
    }

    /** Maps a Nominatim {@code jsonv2} search response (an array) to its first hit; package-private for tests. */
    Match parseSearch(String body) {
        JsonNode root = readTree(body);
        if (root == null || !root.isArray() || root.isEmpty()) throw new LookupException("No place found with that name");
        JsonNode first = root.get(0);
        Double lat = number(first, "lat");
        Double lon = number(first, "lon");
        if (lat == null || lon == null) throw new LookupException("Unreadable answer from OpenStreetMap");
        return new Match(lat, lon, placeFrom(first, "No place found with that name"));
    }

    private JsonNode readTree(String body) {
        try {
            return json.readTree(body);
        } catch (IOException e) {
            throw new LookupException("Unreadable answer from OpenStreetMap");
        }
    }

    /** Name from city → town → village → top-level name, country from {@code address.country_code}. */
    private static Place placeFrom(JsonNode item, String notFound) {
        JsonNode address = item.path("address");
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
            name = text(item, "name");
            kind = "place";
        }
        if (name == null) throw new LookupException(notFound);
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

    /** Nominatim sends coordinates as strings ({@code "13.7524938"}). */
    private static Double number(JsonNode node, String field) {
        String v = text(node, field);
        if (v == null) return null;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText().trim() : null;
    }
}
