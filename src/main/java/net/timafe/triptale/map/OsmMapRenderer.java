package net.timafe.triptale.map;

import net.timafe.triptale.util.Coordinates.LatLon;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Renders a trip map by stitching OSM tiles and drawing pins with plain JDK AWT (todo 61):
 * replaces the old Mapbox static map so the result can be saved and published — Mapbox's terms
 * forbade storing/publishing its images, OSM-family raster tiles are CC BY-SA and fine with
 * attribution. See {@code docs/61_osm_trip_maps.md}.
 *
 * <p>No JavaFX imports (package boundary rule) — {@link net.timafe.triptale.ui.dialog.MapAssistantDialog}
 * turns the resulting file into a JavaFX {@code Image} for display.
 */
public class OsmMapRenderer {

    /**
     * How far the map zooms out around the plotted pins — same four named levels Map Assistant
     * showed for Mapbox, reinterpreted as a margin-around-bbox fraction for the fit-to-bounds
     * math (for multiple points) or a fixed zoom (for a single point, where fitting a
     * zero-size bbox would otherwise zoom all the way to {@link TileSource#maxZoom()}).
     */
    public enum ZoomLevel {
        CLOSE("Close", 0.02, 16),
        NORMAL("Normal", 0.12, 12),
        WIDE("Wide", 0.28, 9),
        VERY_WIDE("Very wide", 0.45, 5);

        private final String label;
        /** Package-private: read by {@link OsmMapRenderer#fitZoom} and tests. */
        final double marginFraction;
        final int singlePointZoom;

        ZoomLevel(String label, double marginFraction, int singlePointZoom) {
            this.label = label;
            this.marginFraction = marginFraction;
            this.singlePointZoom = singlePointZoom;
        }

        @Override public String toString() {
            return label;
        }
    }

    public static final ZoomLevel DEFAULT_ZOOM = ZoomLevel.NORMAL;

    /** Pins drawn beyond this many photo locations would clutter the image and slow rendering. */
    public static final int MAX_PINS = 150;

    private static final Color START_COLOR = new Color(0xf7, 0x4e, 0x4e);
    private static final Color STOP_COLOR = new Color(0x3b, 0xb2, 0xd0);
    private static final Color PHOTO_COLOR = new Color(0xf5, 0xc2, 0x42);
    private static final Color BACKGROUND = new Color(0xdd, 0xdd, 0xdd);
    private static final Color PIN_OUTLINE = new Color(0x22, 0x22, 0x22);
    private static final Color PIN_SHADOW = new Color(0, 0, 0, 70);
    /** Radius of the colored center; the dark outline and white ring add a few more px on top. */
    private static final int PIN_RADIUS = 8;
    private static final int TILE_SIZE = WebMercator.TILE_SIZE;

    private final OsmTileClient tileClient;

    public OsmMapRenderer(OsmTileClient tileClient) {
        this.tileClient = tileClient;
    }

    /**
     * Renders {@code points} onto a {@code width}x{@code height} PNG and saves it to
     * {@code target} (parent directories created on demand, an existing file overwritten).
     * Blocks (tile fetches): call off the FX thread.
     */
    public void render(MapPoints points, TileSource source, ZoomLevel zoom, int width, int height, Path target) {
        BufferedImage image = renderImage(points, source, zoom, width, height);
        try {
            Files.createDirectories(target.getParent());
            ImageIO.write(image, "png", target.toFile());
        } catch (IOException e) {
            throw new OsmTileClient.TileException("Could not save map to " + target, e);
        }
    }

    /** Same as {@link #render}, returning the image without saving it — mainly for tests. */
    public BufferedImage renderImage(MapPoints points, TileSource source, ZoomLevel zoom, int width, int height) {
        if (points.isEmpty()) throw new IllegalArgumentException("No points to plot");
        if (zoom == null) zoom = DEFAULT_ZOOM;
        List<LatLon> all = points.all();

        int z = fitZoom(all, width, height, zoom, source.maxZoom());
        double[] center = center(all, z);
        double topLeftX = center[0] - width / 2.0;
        double topLeftY = center[1] - height / 2.0;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, width, height);

        drawTiles(g, source, z, topLeftX, topLeftY, width, height);

        points.photos().forEach(p -> drawPin(g, p, z, topLeftX, topLeftY, PHOTO_COLOR));
        points.starts().forEach(p -> drawPin(g, p, z, topLeftX, topLeftY, START_COLOR));
        if (points.stop() != null) drawPin(g, points.stop(), z, topLeftX, topLeftY, STOP_COLOR);

        drawAttribution(g, source, width, height);
        g.dispose();
        return image;
    }

    /** package-private for tests. */
    static int fitZoom(List<LatLon> all, int width, int height, ZoomLevel zoom, int maxZoom) {
        boolean singlePoint = new HashSet<>(all).size() == 1;
        return singlePoint ? Math.min(zoom.singlePointZoom, maxZoom)
                : WebMercator.fitZoom(all, width, height, zoom.marginFraction, maxZoom);
    }

    /** The world-pixel center of {@code all}'s bounding box at zoom {@code z}. */
    private static double[] center(List<LatLon> all, int z) {
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        for (LatLon p : all) {
            minLat = Math.min(minLat, p.lat());
            maxLat = Math.max(maxLat, p.lat());
            minLon = Math.min(minLon, p.lon());
            maxLon = Math.max(maxLon, p.lon());
        }
        double centerX = (WebMercator.pixelX(minLon, z) + WebMercator.pixelX(maxLon, z)) / 2.0;
        // y grows southward, so the smaller y comes from the larger (northern) latitude
        double centerY = (WebMercator.pixelY(maxLat, z) + WebMercator.pixelY(minLat, z)) / 2.0;
        return new double[] {centerX, centerY};
    }

    private void drawTiles(Graphics2D g, TileSource source, int z, double topLeftX, double topLeftY, int width, int height) {
        int tilesPerSide = (int) Math.round(WebMercator.tilesPerSide(z));
        int firstTileX = (int) Math.floor(topLeftX / TILE_SIZE);
        int lastTileX = (int) Math.floor((topLeftX + width - 1) / TILE_SIZE);
        int firstTileY = (int) Math.floor(topLeftY / TILE_SIZE);
        int lastTileY = (int) Math.floor((topLeftY + height - 1) / TILE_SIZE);
        for (int tx = firstTileX; tx <= lastTileX; tx++) {
            int wrappedX = Math.floorMod(tx, tilesPerSide);
            for (int ty = firstTileY; ty <= lastTileY; ty++) {
                if (ty < 0 || ty >= tilesPerSide) continue; // no tiles beyond the poles
                int drawX = (int) Math.round(tx * (double) TILE_SIZE - topLeftX);
                int drawY = (int) Math.round(ty * (double) TILE_SIZE - topLeftY);
                try {
                    BufferedImage tile = tileClient.tile(source, z, wrappedX, ty);
                    g.drawImage(tile, drawX, drawY, null);
                } catch (OsmTileClient.TileException e) {
                    // leave the background fill showing through for this one tile
                }
            }
        }
    }

    /**
     * A dark outline + white ring + a soft shadow around the colored center — a flat colored
     * circle alone (the first version of this) got lost against the tile colors (a yellow photo
     * pin against a yellow road, pins against green/blue terrain); the dark ring keeps contrast
     * on every tile style, light or dark.
     */
    private static void drawPin(Graphics2D g, LatLon p, int z, double topLeftX, double topLeftY, Color color) {
        int x = (int) Math.round(WebMercator.pixelX(p.lon(), z) - topLeftX);
        int y = (int) Math.round(WebMercator.pixelY(p.lat(), z) - topLeftY);
        int outer = PIN_RADIUS + 3;
        int mid = PIN_RADIUS + 1;
        g.setColor(PIN_SHADOW);
        g.fillOval(x - outer + 1, y - outer + 2, outer * 2, outer * 2);
        g.setColor(PIN_OUTLINE);
        g.fillOval(x - outer, y - outer, outer * 2, outer * 2);
        g.setColor(Color.WHITE);
        g.fillOval(x - mid, y - mid, mid * 2, mid * 2);
        g.setColor(color);
        g.fillOval(x - PIN_RADIUS, y - PIN_RADIUS, PIN_RADIUS * 2, PIN_RADIUS * 2);
    }


    private static void drawAttribution(Graphics2D g, TileSource source, int width, int height) {
        String text = source.attribution();
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int padding = 4;
        int barHeight = metrics.getHeight() + padding;
        int barY = height - barHeight;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
        g.setColor(Color.WHITE);
        g.fillRect(width - textWidth - padding * 2, barY, textWidth + padding * 2, barHeight);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(Color.DARK_GRAY);
        g.drawString(text, width - textWidth - padding, height - padding - metrics.getDescent());
    }
}
