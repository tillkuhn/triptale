package net.timafe.triptale.map;

import com.sun.net.httpserver.HttpServer;
import net.timafe.triptale.util.Coordinates.LatLon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsmMapRendererTest {

    @TempDir
    Path cacheRoot;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void fitZoom_singlePointUsesTheZoomLevelsFixedZoomCappedAtMaxZoom() {
        List<LatLon> point = List.of(new LatLon(51.5, 8.0));
        assertEquals(16, OsmMapRenderer.fitZoom(point, 960, 640, OsmMapRenderer.ZoomLevel.CLOSE, 17));
        assertEquals(5, OsmMapRenderer.fitZoom(point, 960, 640, OsmMapRenderer.ZoomLevel.VERY_WIDE, 17));
        // Max zoom caps it even when the level would ask for more
        assertEquals(10, OsmMapRenderer.fitZoom(point, 960, 640, OsmMapRenderer.ZoomLevel.CLOSE, 10));
    }

    @Test
    void fitZoom_multiplePointsDelegatesToWebMercatorFitZoom() {
        List<LatLon> points = List.of(new LatLon(50.0, 7.0), new LatLon(52.0, 9.0));
        int expected = WebMercator.fitZoom(points, 960, 640, OsmMapRenderer.ZoomLevel.NORMAL.marginFraction, 17);
        assertEquals(expected, OsmMapRenderer.fitZoom(points, 960, 640, OsmMapRenderer.ZoomLevel.NORMAL, 17));
    }

    @Test
    void renderImage_producesTheRequestedSize() throws IOException {
        URI base = serve(200, solidTile(Color.GREEN));
        OsmMapRenderer renderer = renderer(base);
        MapPoints points = MapPoints.of(List.of()).withPhotos(List.of(), 0);
        points = pointsOf(new LatLon(51.5, 8.0));

        BufferedImage image = renderer.renderImage(points, TileSource.OPEN_TOPO_MAP, OsmMapRenderer.ZoomLevel.NORMAL, 400, 300);

        assertEquals(400, image.getWidth());
        assertEquals(300, image.getHeight());
    }

    @Test
    void renderImage_drawsAStartPinNearTheCenterForASinglePoint() throws IOException {
        URI base = serve(200, solidTile(Color.WHITE));
        OsmMapRenderer renderer = renderer(base);
        MapPoints points = pointsOf(new LatLon(51.5, 8.0));

        BufferedImage image = renderer.renderImage(points, TileSource.OPEN_TOPO_MAP, OsmMapRenderer.ZoomLevel.NORMAL, 400, 300);

        // The single point is centered; its red start pin should tint the very center pixel.
        int rgb = image.getRGB(200, 150);
        Color center = new Color(rgb);
        assertTrue(center.getRed() > center.getBlue(), "center pixel should be reddish (start pin), was " + center);
    }

    @Test
    void renderImage_rejectsEmptyPoints() throws IOException {
        OsmMapRenderer renderer = renderer(serve(200, solidTile(Color.WHITE)));
        assertThrows(IllegalArgumentException.class,
                () -> renderer.renderImage(MapPoints.of(List.of()), TileSource.OPEN_TOPO_MAP, OsmMapRenderer.ZoomLevel.NORMAL, 100, 100));
    }

    @Test
    void render_savesAReadablePngToTheTargetPath(@TempDir Path tempDir) throws IOException {
        OsmMapRenderer renderer = renderer(serve(200, solidTile(Color.BLUE)));
        MapPoints points = pointsOf(new LatLon(10.0, 20.0));
        Path target = tempDir.resolve("maps").resolve("map-2026-01-01-2026-01-02.png");

        renderer.render(points, TileSource.OPEN_TOPO_MAP, OsmMapRenderer.ZoomLevel.WIDE, 200, 150, target);

        assertTrue(Files.exists(target));
        BufferedImage saved = ImageIO.read(target.toFile());
        assertEquals(200, saved.getWidth());
        assertEquals(150, saved.getHeight());
    }

    private static MapPoints pointsOf(LatLon p) {
        return MapPoints.of(List.of()).withPhotos(List.of(p), 10);
    }

    private OsmMapRenderer renderer(URI base) {
        return new OsmMapRenderer(new OsmTileClient(cacheRoot, "test", HttpClient.newBuilder().build(), base));
    }

    private static byte[] solidTile(Color color) throws IOException {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 256, 256);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private URI serve(int status, byte[] body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}
