package net.timafe.triptale.map;

import com.sun.net.httpserver.HttpServer;
import net.timafe.triptale.map.PlaceLookup.LookupException;
import net.timafe.triptale.map.PlaceLookup.Match;
import net.timafe.triptale.map.PlaceLookup.Place;
import net.timafe.triptale.map.PlaceLookup.Query;
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

    @Test
    void splitQuery_takesTrailingTwoLetterCodeOnly() {
        assertEquals(new Query("Bangkok", "th"), PlaceLookup.splitQuery("  Bangkok, TH "));
        assertEquals(new Query("Frankfurt am Main", "de"), PlaceLookup.splitQuery("Frankfurt am Main,de"));
        assertEquals(new Query("Bangkok", null), PlaceLookup.splitQuery("Bangkok"));
        assertEquals(new Query("Paris, Texas", null), PlaceLookup.splitQuery("Paris, Texas"));
        assertEquals(new Query("Ulm, DEU", null), PlaceLookup.splitQuery("Ulm, DEU"));
        assertEquals(new Query("", null), PlaceLookup.splitQuery(null));
    }

    @Test
    void labelFor_keepsTypedNameAndAppendsHitCountry() {
        Place th = new Place("Bangkok", "TH", "city");
        assertEquals("Krung Thep Aphiwat, TH", PlaceLookup.labelFor("Krung Thep Aphiwat", th));
        assertEquals("Krung Thep Aphiwat, TH", PlaceLookup.labelFor(" Krung Thep Aphiwat ,th ", th));
        assertEquals("Hamburg, US", PlaceLookup.labelFor("Hamburg", new Place("Hamburg", "US", "town")));
        assertEquals("Ulm, DE", PlaceLookup.labelFor("ulm,de", new Place("Ulm", null, "place")));
        assertEquals("Essen, DE", PlaceLookup.labelFor("essen", new Place("Essen", "DE", "city")));
        assertEquals("Atlantis", PlaceLookup.labelFor("Atlantis", new Place("Atlantis", null, "place")));
    }

    @Test
    void parseSearch_takesFirstHit() {
        Match match = parser.parseSearch("""
                [{"lat":"13.7524938","lon":"100.4935089","name":"Bangkok",
                  "address":{"city":"Bangkok","country_code":"th"}},
                 {"lat":"1","lon":"2","name":"Other"}]""");
        assertEquals(new Match(13.7524938, 100.4935089, new Place("Bangkok", "TH", "city")), match);
    }

    @Test
    void parseSearch_emptyOrBrokenIsNoPlace() {
        LookupException e = assertThrows(LookupException.class, () -> parser.parseSearch("[]"));
        assertEquals("No place found with that name", e.getMessage());
        assertThrows(LookupException.class, () -> parser.parseSearch("[{\"name\":\"X\"}]"));
        assertThrows(LookupException.class, () -> parser.parseSearch("{\"error\":\"x\"}"));
    }

    @Test
    void search_sendsEncodedQueryAndCountryCode() throws IOException {
        List<String> queries = new CopyOnWriteArrayList<>();
        URI base = serve(200, "[{\"lat\":\"48.4\",\"lon\":\"9.99\",\"address\":{\"town\":\"Ulm\",\"country_code\":\"de\"}}]",
                new CopyOnWriteArrayList<>(), queries);
        PlaceLookup lookup = new PlaceLookup(base, null, Duration.ZERO);

        Match match = lookup.search("Neu-Ulm Süd, DE");
        lookup.search("São Paulo");

        assertEquals(48.4, match.lat());
        assertEquals("Ulm, DE", match.place().label());
        assertEquals("q=Neu-Ulm+S%C3%BCd&countrycodes=de&format=jsonv2&limit=1&addressdetails=1&accept-language=en",
                queries.get(0));
        assertEquals("q=S%C3%A3o+Paulo&format=jsonv2&limit=1&addressdetails=1&accept-language=en", queries.get(1));
    }

    @Test
    void search_blankTextFailsWithoutRequest() {
        LookupException e = assertThrows(LookupException.class, () -> parser.search(" , DE"));
        assertEquals("Enter a place name first", e.getMessage());
    }

    private URI serve(int status, String body, List<String> agents, List<String> queries) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
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
