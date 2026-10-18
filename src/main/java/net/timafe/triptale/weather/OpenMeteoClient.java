package net.timafe.triptale.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.timafe.triptale.domain.Weather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Fetches a day's weather from Open-Meteo's forecast API (todo 65): free, no API key needed for
 * non-commercial use, covering roughly the past 92 days through 16 days ahead. A date outside
 * that window, or any other failure, surfaces as {@link WeatherException} with a short message
 * meant for a debug log — never a user-facing alert, since weather is background enrichment,
 * not load-bearing. No JavaFX imports (package boundary rule).
 */
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    /** A constant, not a setting: the historical archive API has a different base and response shape. */
    static final URI FORECAST = URI.create("https://api.open-meteo.com");
    static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** Base of the historical archive API (unlike {@link #FORECAST}, kept forever, not just ~92 days). */
    private static final String ARCHIVE_BASE = "https://archive-api.open-meteo.com";

    /** A failed fetch; the message is short and human, meant for a debug log, not an alert. */
    public static class WeatherException extends RuntimeException {
        public WeatherException(String message) { super(message); }
    }

    private final URI baseUrl;
    private final String userAgent;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public OpenMeteoClient(String appVersion) {
        this(FORECAST, appVersion);
    }

    OpenMeteoClient(URI baseUrl, String appVersion) {
        this.baseUrl = baseUrl;
        this.userAgent = "TripTale/" + (appVersion == null || appVersion.isBlank() ? "dev" : appVersion)
                + " (+https://github.com/tillkuhn/triptale)";
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /**
     * The day's min/max temperature (whole °C) and weather code for a point. Blocks (network):
     * call off the FX thread. Throws {@link WeatherException} for a date outside the forecast
     * window, a network error, or an unreadable answer.
     */
    public Weather forecast(double lat, double lon, LocalDate date) {
        String path = String.format(Locale.ROOT,
                "/v1/forecast?latitude=%.6f&longitude=%.6f&start_date=%s&end_date=%s"
                        + "&daily=weather_code,temperature_2m_min,temperature_2m_max&timezone=auto",
                lat, lon, date, date);
        String body = get(path);
        Weather weather = parse(body, date);
        log.debug("Weather forecast for {}: {}", date, weather.toRaw());
        return weather;
    }

    /**
     * A browser-openable URL showing the raw daily data for a point and date, so a recorded
     * {@link Weather} can be checked against the source later — unlike {@link #FORECAST}, the
     * archive API keeps data indefinitely rather than just ~92 days, so this stays valid for any
     * past date. Opens as JSON, not a page, but that's the full detail behind the min/max/code.
     */
    public static String archiveUrl(double lat, double lon, LocalDate date) {
        return String.format(Locale.ROOT,
                ARCHIVE_BASE + "/v1/archive?latitude=%.6f&longitude=%.6f&start_date=%s&end_date=%s"
                        + "&daily=weather_code,temperature_2m_min,temperature_2m_max&timezone=auto",
                lat, lon, date, date);
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
        try {
            log.debug("Weather GET {}", uri);
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new WeatherException("Timed out after " + TIMEOUT.toSeconds() + " s");
        } catch (IOException e) {
            throw new WeatherException("Network error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeatherException("Interrupted");
        }
        log.debug("Weather HTTP {}: {}", response.statusCode(), response.body());
        if (response.statusCode() != 200) {
            throw new WeatherException("Open-Meteo answered HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** Maps the forecast response's {@code daily} arrays to the entry matching {@code date}; package-private for tests. */
    Weather parse(String body, LocalDate date) {
        JsonNode root = readTree(body);
        JsonNode daily = root.path("daily");
        JsonNode times = daily.path("time");
        int index = -1;
        for (int i = 0; i < times.size(); i++) {
            if (date.toString().equals(times.get(i).asText())) {
                index = i;
                break;
            }
        }
        if (index < 0) throw new WeatherException("No weather data for " + date);
        Double min = number(daily.path("temperature_2m_min"), index);
        Double max = number(daily.path("temperature_2m_max"), index);
        Double code = number(daily.path("weather_code"), index);
        if (min == null || max == null || code == null) throw new WeatherException("Unreadable answer from Open-Meteo");
        return new Weather((int) Math.round(min), (int) Math.round(max), (int) Math.round(code), null);
    }

    private JsonNode readTree(String body) {
        try {
            return json.readTree(body);
        } catch (IOException e) {
            throw new WeatherException("Unreadable answer from Open-Meteo");
        }
    }

    private static Double number(JsonNode array, int index) {
        if (!array.isArray() || index >= array.size()) return null;
        JsonNode v = array.get(index);
        return v.isNumber() ? v.asDouble() : null;
    }
}
