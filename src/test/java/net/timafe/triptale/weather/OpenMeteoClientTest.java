package net.timafe.triptale.weather;

import com.sun.net.httpserver.HttpServer;
import net.timafe.triptale.domain.Weather;
import net.timafe.triptale.weather.OpenMeteoClient.WeatherException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenMeteoClientTest {

    private final OpenMeteoClient parser = new OpenMeteoClient("test");
    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(s -> s.stop(0));
    }

    @Test
    void parse_picksTheMatchingDateFromTheDailyArrays() {
        Weather w = parser.parse("""
                {"daily":{"time":["2026-07-02","2026-07-03"],
                           "temperature_2m_min":[10.4,15.1],
                           "temperature_2m_max":[18.6,22.9],
                           "weather_code":[3,61]}}""", LocalDate.of(2026, 7, 3));
        assertEquals(new Weather(15, 23, 61, null), w);
    }

    @Test
    void parse_dateNotInRangeThrows() {
        WeatherException e = assertThrows(WeatherException.class, () -> parser.parse("""
                {"daily":{"time":["2026-07-02"],"temperature_2m_min":[10.0],
                           "temperature_2m_max":[18.0],"weather_code":[0]}}""", LocalDate.of(2026, 7, 3)));
        assertEquals("No weather data for 2026-07-03", e.getMessage());
    }

    @Test
    void parse_unreadableAnswerThrows() {
        assertThrows(WeatherException.class, () -> parser.parse("not json", LocalDate.of(2026, 7, 3)));
    }

    @Test
    void forecast_sendsUserAgentAndQuery() throws IOException {
        List<String> agents = new CopyOnWriteArrayList<>();
        List<String> paths = new CopyOnWriteArrayList<>();
        URI base = serve(200, """
                {"daily":{"time":["2026-07-03"],"temperature_2m_min":[15.4],
                           "temperature_2m_max":[22.1],"weather_code":[1]}}""", agents, paths);

        Weather w = new OpenMeteoClient(base, "v1.2.3").forecast(51.478536, 8.336688, LocalDate.of(2026, 7, 3));

        assertEquals(new Weather(15, 22, 1, null), w);
        assertEquals("TripTale/v1.2.3 (+https://github.com/tillkuhn/triptale)", agents.getFirst());
        assertEquals("/v1/forecast?latitude=51.478536&longitude=8.336688&start_date=2026-07-03"
                + "&end_date=2026-07-03&daily=weather_code,temperature_2m_min,temperature_2m_max&timezone=auto",
                paths.getFirst());
    }

    @Test
    void forecast_httpErrorBecomesShortMessage() throws IOException {
        URI base = serve(429, "slow down", new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());
        WeatherException e = assertThrows(WeatherException.class,
                () -> new OpenMeteoClient(base, null).forecast(1, 2, LocalDate.of(2026, 7, 3)));
        assertEquals("Open-Meteo answered HTTP 429", e.getMessage());
    }

    @Test
    void archive_sendsArchivePathToTheArchiveBase() throws IOException {
        List<String> agents = new CopyOnWriteArrayList<>();
        List<String> paths = new CopyOnWriteArrayList<>();
        URI forecastBase = URI.create("http://127.0.0.1:1");
        URI archiveBase = serve(200, """
                {"daily":{"time":["2020-01-15"],"temperature_2m_min":[-2.4],
                           "temperature_2m_max":[3.1],"weather_code":[71]}}""", agents, paths);

        Weather w = new OpenMeteoClient(forecastBase, archiveBase, "v1.2.3")
                .archive(51.478536, 8.336688, LocalDate.of(2020, 1, 15));

        assertEquals(new Weather(-2, 3, 71, null), w);
        assertEquals("/v1/archive?latitude=51.478536&longitude=8.336688&start_date=2020-01-15"
                + "&end_date=2020-01-15&daily=weather_code,temperature_2m_min,temperature_2m_max&timezone=auto",
                paths.getFirst());
    }

    @Test
    void fetch_usesForecastWhenItHasTheDate() throws IOException {
        List<String> forecastPaths = new CopyOnWriteArrayList<>();
        URI forecastBase = serve(200, """
                {"daily":{"time":["2026-07-03"],"temperature_2m_min":[10.0],
                           "temperature_2m_max":[20.0],"weather_code":[0]}}""",
                new CopyOnWriteArrayList<>(), forecastPaths);
        URI archiveBase = URI.create("http://127.0.0.1:1");

        Weather w = new OpenMeteoClient(forecastBase, archiveBase, "v1.2.3")
                .fetch(1, 2, LocalDate.of(2026, 7, 3));

        assertEquals(new Weather(10, 20, 0, null), w);
        assertEquals(1, forecastPaths.size());
    }

    @Test
    void fetch_fallsBackToArchiveWhenForecastHasNoData() throws IOException {
        List<String> archivePaths = new CopyOnWriteArrayList<>();
        // Forecast answers 200 but without the requested date (e.g. too far outside its window) —
        // same "empty daily block" shape Open-Meteo uses for a date it doesn't cover.
        URI forecastBase = serve(200, """
                {"daily":{"time":[],"temperature_2m_min":[],
                           "temperature_2m_max":[],"weather_code":[]}}""",
                new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());
        URI archiveBase = serve(200, """
                {"daily":{"time":["2020-01-15"],"temperature_2m_min":[-5.0],
                           "temperature_2m_max":[2.0],"weather_code":[73]}}""",
                new CopyOnWriteArrayList<>(), archivePaths);

        Weather w = new OpenMeteoClient(forecastBase, archiveBase, "v1.2.3")
                .fetch(1, 2, LocalDate.of(2020, 1, 15));

        assertEquals(new Weather(-5, 2, 73, null), w);
        assertEquals(1, archivePaths.size());
    }

    @Test
    void fetch_reportsTheForecastFailureWhenBothEndpointsFail() throws IOException {
        URI forecastBase = serve(429, "slow down", new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());
        URI archiveBase = serve(503, "unavailable", new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());

        WeatherException e = assertThrows(WeatherException.class, () ->
                new OpenMeteoClient(forecastBase, archiveBase, "v1.2.3").fetch(1, 2, LocalDate.of(2026, 7, 3)));

        assertEquals("Open-Meteo answered HTTP 429", e.getMessage());
    }

    private URI serve(int status, String body, List<String> agents, List<String> paths) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            agents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            paths.add(exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        servers.add(server);
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}
