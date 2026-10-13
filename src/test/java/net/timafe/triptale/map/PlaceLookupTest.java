package net.timafe.triptale.map;

import com.sun.net.httpserver.HttpServer;
import net.timafe.triptale.map.PlaceLookup.LookupException;
import net.timafe.triptale.map.PlaceLookup.Place;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceLookupTest {

    private final PlaceLookup parser = new PlaceLookup("test");
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void parse_prefersCityThenTownThenVillage() {
        Place city = parser.parse("""
                {"name":"Ignored","address":{"village":"V","town":"T","city":"Frankfurt am Main","country_code":"de"}}""");
        assertEquals(new Place("Frankfurt am Main", "DE", "city"), city);
        assertEquals("Frankfurt am Main, DE", city.label());

        Place town = parser.parse("""
                {"address":{"village":"V","town":"Soest","country_code":"de"}}""");
        assertEquals(new Place("Soest", "DE", "town"), town);

        Place village = parser.parse("""
                {"address":{"village":"Möhnesee","country_code":"de"}}""");
        assertEquals(new Place("Möhnesee", "DE", "village"), village);
    }

    @Test
    void parse_fallsBackToNameAndToNoCountry() {
        Place place = parser.parse("""
                {"name":"Arkona","address":{}}""");
        assertEquals("place", place.kind());
        assertNull(place.countryCode());
        assertEquals("Arkona", place.label());
    }

    @Test
    void parse_errorOrNoNameIsNoPlace() {
        LookupException e = assertThrows(LookupException.class,
                () -> parser.parse("{\"error\":\"Unable to geocode\"}"));
        assertEquals("No place found here", e.getMessage());
        assertThrows(LookupException.class, () -> parser.parse("{\"address\":{\"country_code\":\"de\"}}"));
        assertThrows(LookupException.class, () -> parser.parse("not json"));
    }

    @Test
    void lookup_sendsUserAgentAndQuery() throws IOException {
        List<String> agents = new CopyOnWriteArrayList<>();
        List<String> queries = new CopyOnWriteArrayList<>();
        URI base = serve(200, "{\"address\":{\"town\":\"Soest\",\"country_code\":\"de\"}}", agents, queries);

        Place place = new PlaceLookup(base, "v1.2.3", Duration.ZERO).lookup(51.478536, 8.336688);

        assertEquals("Soest, DE", place.label());
        assertEquals("TripTale/v1.2.3 (+https://github.com/tillkuhn/triptale)", agents.getFirst());
        assertEquals("lat=51.478536&lon=8.336688&format=jsonv2&zoom=10&accept-language=en", queries.getFirst());
    }

    @Test
    void lookup_httpErrorBecomesShortMessage() throws IOException {
        URI base = serve(429, "slow down", new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());
        LookupException e = assertThrows(LookupException.class,
                () -> new PlaceLookup(base, null, Duration.ZERO).lookup(1, 2));
        assertEquals("OpenStreetMap answered HTTP 429", e.getMessage());
    }

    @Test
    void lookup_waitsOutTheMinimumInterval() throws IOException {
        URI base = serve(200, "{\"name\":\"X\"}", new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());
        PlaceLookup lookup = new PlaceLookup(base, null, Duration.ofMillis(300));

        long start = System.nanoTime();
        lookup.lookup(1, 2);
        lookup.lookup(1, 2);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(elapsedMs >= 300, "second request should wait, took " + elapsedMs + " ms");
    }

    private URI serve(int status, String body, List<String> agents, List<String> queries) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reverse", exchange -> {
            agents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            queries.add(exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}
