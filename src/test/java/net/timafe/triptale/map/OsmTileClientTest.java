package net.timafe.triptale.map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsmTileClientTest {

    @TempDir
    Path cacheRoot;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void tile_fetchesCachesAndReusesFromDisk() throws IOException {
        List<String> agents = new CopyOnWriteArrayList<>();
        AtomicInteger requests = new AtomicInteger();
        byte[] png = pngBytes(4, 4);
        URI base = serve(200, png, agents, requests);
        OsmTileClient client = new OsmTileClient(cacheRoot, "v1.2.3",
                HttpClient.newBuilder().build(), base);

        BufferedImage first = client.tile(TileSource.OPEN_TOPO_MAP, 5, 10, 12);
        assertNotNull(first);
        assertEquals(4, first.getWidth());
        assertEquals(1, requests.get());
        assertEquals("TripTale/v1.2.3 (+https://github.com/tillkuhn/triptale)", agents.getFirst());
        Path cached = cacheRoot.resolve("open_topo_map").resolve("5").resolve("10").resolve("12.png");
        assertTrue(Files.exists(cached), "tile should be written to the disk cache");

        BufferedImage second = client.tile(TileSource.OPEN_TOPO_MAP, 5, 10, 12);
        assertNotNull(second);
        assertEquals(1, requests.get(), "second call should be served from the cache, no extra request");
    }

    @Test
    void tile_httpErrorBecomesTileException() throws IOException {
        URI base = serve(404, "not found".getBytes(), new CopyOnWriteArrayList<>(), new AtomicInteger());
        OsmTileClient client = new OsmTileClient(cacheRoot, null, HttpClient.newBuilder().build(), base);

        OsmTileClient.TileException e = assertThrows(OsmTileClient.TileException.class,
                () -> client.tile(TileSource.OPEN_TOPO_MAP, 1, 0, 0));
        assertTrue(e.getMessage().contains("HTTP 404"));
    }

    @Test
    void tile_corruptCacheEntryIsRefetched() throws IOException {
        Path cached = cacheRoot.resolve("open_topo_map").resolve("3").resolve("1").resolve("1.png");
        Files.createDirectories(cached.getParent());
        Files.write(cached, "not a png".getBytes());
        AtomicInteger requests = new AtomicInteger();
        URI base = serve(200, pngBytes(2, 2), new CopyOnWriteArrayList<>(), requests);
        OsmTileClient client = new OsmTileClient(cacheRoot, null, HttpClient.newBuilder().build(), base);

        BufferedImage image = client.tile(TileSource.OPEN_TOPO_MAP, 3, 1, 1);

        assertNotNull(image);
        assertEquals(1, requests.get(), "a corrupt cache entry should be refetched, not reused");
    }

    private static byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private URI serve(int status, byte[] body, List<String> agents, AtomicInteger requests) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            agents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}
