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
    void fitZoom_multiplePointsAppliesTheZoomLevelsBiasOverTheBaseFit() {
        List<LatLon> points = List.of(new LatLon(50.0, 7.0), new LatLon(52.0, 9.0));
        int base = WebMercator.fitZoom(points, 960, 640, 0.12, 17);
        assertEquals(base, OsmMapRenderer.fitZoom(points, 960, 640, OsmMapRenderer.ZoomLevel.NORMAL, 17));
        assertEquals(base + 2, OsmMapRenderer.fitZoom(points, 960, 640, OsmMapRenderer.ZoomLevel.CLOSE, 17));
        assertEquals(base + 1, OsmMapRenderer.fitZoom(points, 960, 640, OsmMapRenderer.ZoomLevel.SNUG, 17));
        assertEquals(base - 1, OsmMapRenderer.fitZoom(points, 960, 640, OsmMapRenderer.ZoomLevel.WIDE, 17));
        assertEquals(base - 2, OsmMapRenderer.fitZoom(points, 960, 640, OsmMapRenderer.ZoomLevel.WIDER, 17));
        assertEquals(base - 3, OsmMapRenderer.fitZoom(points, 960, 640, OsmMapRenderer.ZoomLevel.VERY_WIDE, 17));
    }


    @Test
    void fitZoom_multiplePointsClampsTheBiasedZoomToMaxZoomAndZero() {
        // A close pair of points fits at maxZoom already with lots of room to spare — CLOSE's
        // +2 bias must not push it past maxZoom, nor VERY_WIDE's -3 bias below zero.
        List<LatLon> tight = List.of(new LatLon(51.5, 8.0), new LatLon(51.5001, 8.0001));
        assertEquals(17, OsmMapRenderer.fitZoom(tight, 960, 640, OsmMapRenderer.ZoomLevel.CLOSE, 17));
        assertEquals(0, OsmMapRenderer.fitZoom(tight, 1, 1, OsmMapRenderer.ZoomLevel.VERY_WIDE, 1));
    }

    @Test
    void renderImage_producesTheRequestedSize() throws IOException {
        URI base = serve(200, solidTile(Color.GREEN));
        OsmMapRenderer renderer = renderer(base);
        MapPoints points = pointsOf(new LatLon(51.5, 8.0));

        BufferedImage image = renderer.renderImage(points, TileSource.OPEN_TOPO_MAP, OsmMapRenderer.ZoomLevel.NORMAL, 400, 300).image();

        assertEquals(400, image.getWidth());
        assertEquals(300, image.getHeight());
    }

    @Test
    void renderImage_drawsAStartPinNearTheCenterForASinglePoint() throws IOException {
        URI base = serve(200, solidTile(Color.WHITE));
        OsmMapRenderer renderer = renderer(base);
        MapPoints points = pointsOf(new LatLon(51.5, 8.0));

        BufferedImage image = renderer.renderImage(points, TileSource.OPEN_TOPO_MAP, OsmMapRenderer.ZoomLevel.NORMAL, 400, 300).image();

        // The single point is centered; its red photo pin should tint the very center pixel.
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
        Path target = tempDir.resolve("maps").resolve("tripmap.png");

        MapPoints plotted = renderer.render(points, TileSource.OPEN_TOPO_MAP, OsmMapRenderer.ZoomLevel.WIDE, 200, 150,
                p -> java.util.Map.of("triptale:from", "2026-01-01", "triptale:photosShown", "" + p.photos().size()), target);

        assertTrue(Files.exists(target));
        BufferedImage saved = ImageIO.read(target.toFile());
        assertEquals(200, saved.getWidth());
        assertEquals(150, saved.getHeight());
        assertEquals("2026-01-01", PngText.read(target).get("triptale:from"));
        assertEquals("1", PngText.read(target).get("triptale:photosShown"));
        assertEquals(1, plotted.photos().size());
    }

    private static final int Z = 20;

    /** A point {@code xPixels} world pixels east of 0,0 at zoom {@link #Z}. */
    private static LatLon px(double xPixels) {
        return new LatLon(0.0, xPixels * 360.0 / (WebMercator.TILE_SIZE * WebMercator.tilesPerSide(Z)));
    }

    @Test
    void thinPhotos_dropsOverlappingPhotosAndPhotosOnADayPin() {
        List<LatLon> photos = List.of(px(100), px(110), px(200), px(5));
        // px(110) overlaps px(100) (< 14 px); px(5) sits on the start pin (< 16 px)
        assertEquals(List.of(px(100), px(200)), OsmMapRenderer.thinPhotos(List.of(px(0)), photos, Z));
    }

    @Test
    void thinPhotos_keepsFarApartPhotos() {
        List<LatLon> photos = List.of(px(100), px(120), px(140));
        assertEquals(photos, OsmMapRenderer.thinPhotos(List.of(), photos, Z));
    }

    @Test
    void collapseRoute_dropsOnlyConsecutiveNearPoints_soAReturnLegSurvives() {
        List<LatLon> route = List.of(px(0), px(5), px(300), px(2));
        // px(5) is too close to px(0); px(2) is just as close to px(0) but follows px(300)
        assertEquals(List.of(px(0), px(300), px(2)), OsmMapRenderer.collapseRoute(route, Z));
    }

    private static MapPoints pointsOf(LatLon p) {
        return new MapPoints(List.of(), null, List.of(p), 1, List.of());
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
