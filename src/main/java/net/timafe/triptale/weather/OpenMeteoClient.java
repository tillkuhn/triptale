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
 * Fetches a day's weather from Open-Meteo (todo 65): the forecast API for roughly the past 92
 * days through 16 days ahead, falling back to the historical archive API (ERA5, back to 1940)
 * for anything the forecast endpoint doesn't have — {@link #fetch} tries the forecast endpoint
 * first and only falls back to the archive if that fails, rather than picking a side from the
 * date alone, since the forecast API's actual past coverage turned out to vary (dates well
 * inside the nominal 92-day window still occasionally came back empty, while the archive had
 * them) — so callers never need to care which endpoint actually answered, or guess the cutoff.
 * Free, no API key needed for non-commercial use. Any failure surfaces as {@link WeatherException}
 * with a short message meant for a debug log — never a user-facing alert, since weather is
 * background enrichment, not load-bearing. No JavaFX imports (package boundary rule).
 */
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    /** A constant, not a setting: the historical archive API has a different base and response shape. */
    static final URI FORECAST = URI.create("https://api.open-meteo.com");
    static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** Base of the historical archive API (unlike {@link #FORECAST}, kept forever, not just ~92 days). */
    static final URI ARCHIVE = URI.create("https://archive-api.open-meteo.com");

    /** A failed fetch; the message is short and human, meant for a debug log, not an alert. */
    public static class WeatherException extends RuntimeException {
        public WeatherException(String message) { super(message); }
    }

    private final URI forecastBase;
    private final URI archiveBase;
    private final String userAgent;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public OpenMeteoClient(String appVersion) {
        this(FORECAST, ARCHIVE, appVersion);
    }

    /** Package-private: lets tests point the forecast endpoint at a local server, archive untouched. */
    OpenMeteoClient(URI forecastBase, String appVersion) {
        this(forecastBase, ARCHIVE, appVersion);
    }

    /** Package-private: lets tests point both endpoints at local servers independently. */
    OpenMeteoClient(URI forecastBase, URI archiveBase, String appVersion) {
        this.forecastBase = forecastBase;
        this.archiveBase = archiveBase;
        this.userAgent = "TripTale/" + (appVersion == null || appVersion.isBlank() ? "dev" : appVersion)
                + " (+https://github.com/tillkuhn/triptale)";
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /**
     * The day's min/max temperature (whole °C) and weather code for a point: tries the forecast
     * endpoint first, falling back to the historical archive if that fails for any reason (no
     * data for the date, network error, …) — see the class doc for why this is try-then-fallback
     * rather than a date-based cutoff. Blocks (network), and on the fallback path blocks twice:
     * call off the FX thread. Throws the <strong>forecast</strong> failure's {@link WeatherException}
     * if the archive attempt fails too (the more likely one to be informative — most dates that
     * reach the fallback are simply outside the forecast window, which the archive then explains
     * further, or not, equally silently from the caller's point of view).
     */
    public Weather fetch(double lat, double lon, LocalDate date) {
        try {
            return forecast(lat, lon, date);
        } catch (WeatherException forecastFailure) {
            try {
                return archive(lat, lon, date);
            } catch (WeatherException archiveFailure) {
                throw forecastFailure;
            }
        }
    }

    /**
     * The day's min/max temperature (whole °C) and weather code for a point. Blocks (network):
     * call off the FX thread. Throws {@link WeatherException} for a date outside the forecast
     * window, a network error, or an unreadable answer.
     */
    public Weather forecast(double lat, double lon, LocalDate date) {
        Weather weather = parse(get(forecastBase, dailyPath("/v1/forecast", lat, lon, date)), date);
        log.debug("Weather forecast for {}: {}", date, weather.toRaw());
        return weather;
    }

    /**
     * The day's min/max temperature (whole °C) and weather code for a point, from the
     * historical archive (ERA5, back to 1940) — for dates the forecast API no longer covers.
     * Same request/response shape as {@link #forecast}, different base URL. Blocks (network):
     * call off the FX thread.
     */
    public Weather archive(double lat, double lon, LocalDate date) {
        Weather weather = parse(get(archiveBase, dailyPath("/v1/archive", lat, lon, date)), date);
        log.debug("Weather archive for {}: {}", date, weather.toRaw());
        return weather;
    }

    private static String dailyPath(String endpoint, double lat, double lon, LocalDate date) {
        return String.format(Locale.ROOT,
                endpoint + "?latitude=%.6f&longitude=%.6f&start_date=%s&end_date=%s"
                        + "&daily=weather_code,temperature_2m_min,temperature_2m_max&timezone=auto",
                lat, lon, date, date);
    }

    /**
     * A browser-openable URL showing the raw daily data for a point and date, so a recorded
     * {@link Weather} can be checked against the source later — unlike the forecast endpoint,
     * the archive API keeps data indefinitely rather than just ~92 days, so this stays valid for
     * any past date. Opens as JSON, not a page, but that's the full detail behind the
     * min/max/code.
     */
    public static String archiveUrl(double lat, double lon, LocalDate date) {
        return ARCHIVE + dailyPath("/v1/archive", lat, lon, date);
    }

    /** GETs a path below {@code base} and returns the body of a 200 answer. */
    private String get(URI base, String pathAndQuery) {
        URI uri = base.resolve(pathAndQuery);
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
