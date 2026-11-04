package net.timafe.triptale.map;

import net.timafe.triptale.util.Coordinates.LatLon;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
     * How far the map zooms out around the plotted pins — six named levels (todo 61 follow-up
     * added {@link #SNUG} and {@link #WIDER} between the original four Mapbox-era levels: the
     * jump from Close to Normal, and from Wide to Very wide, was a 2-level {@code zoomBias} step
     * and a 4-level {@link #singlePointZoom} step — too coarse to pick a comfortable in-between
     * view). For multiple points, a shared margin fraction first finds the tightest zoom that
     * fits all of them ({@link #BASE_MARGIN_FRACTION}), then {@code zoomBias} steps that zoom in
     * or out by a fixed number of levels — a margin-only difference between levels is too small
     * to ever flip the (exponential, integer) zoom pick for most real trips, so Close and Normal
     * used to render identically. For a single point, a fixed zoom is used instead, since
     * fitting a zero-size bbox would otherwise zoom all the way to {@link TileSource#maxZoom()}
     * regardless of level.
     */
    public enum ZoomLevel {
        CLOSE("Close", 2, 16),
        SNUG("Snug", 1, 14),
        NORMAL("Normal", 0, 12),
        WIDE("Wide", -1, 9),
        WIDER("Wider", -2, 7),
        VERY_WIDE("Very wide", -3, 5);

        private final String label;
        /** Package-private: read by {@link OsmMapRenderer#fitZoom} and tests. Zoom levels to add
         *  on top of the {@link #BASE_MARGIN_FRACTION}-fitted zoom for multiple points. */
        final int zoomBias;
        final int singlePointZoom;

        ZoomLevel(String label, int zoomBias, int singlePointZoom) {
            this.label = label;
            this.zoomBias = zoomBias;
            this.singlePointZoom = singlePointZoom;
        }

        @Override public String toString() {
            return label;
        }
    }

    public static final ZoomLevel DEFAULT_ZOOM = ZoomLevel.NORMAL;

    /** Shared padding around the bbox when computing the tightest fit for multiple points —
     *  {@link ZoomLevel#zoomBias} does the actual Close/Normal/Wide/Very wide differentiation. */
    private static final double BASE_MARGIN_FRACTION = 0.12;

    /** Safety cap on pins, applied after pixel thinning (todo 78) — that alone normally keeps far fewer. */
    public static final int MAX_PINS = 150;

    /**
     * A pin's look: colored center of {@code radius}, a 1px white ring and a dark outline of
     * {@code outline} px around it. {@code outerRadius} is what thinning measures overlap with.
     */
    record PinStyle(Color fill, int radius, int outline) {
        int outerRadius() {
            return radius + 1 + outline;
        }
    }

    /*
     * Pin styling (todo 78): one red family plus black, ranked by lightness and size rather than
     * hue — survives colour blindness, and dark fills keep contrast on the mostly light tiles.
     * Photos are the most numerous and least important, so they're light and small. Day pins
     * stay a step bigger, but not so big that they hide the town name they sit on.
     */
    static final PinStyle START_PIN = new PinStyle(new Color(0xb7, 0x1c, 0x1c), 6, 2);
    static final PinStyle STOP_PIN = new PinStyle(new Color(0x21, 0x21, 0x21), 6, 2);
    static final PinStyle PHOTO_PIN = new PinStyle(new Color(0xe5, 0x73, 0x73), 5, 1);
    private static final Color BACKGROUND = new Color(0xdd, 0xdd, 0xdd);
    private static final Color PIN_OUTLINE = new Color(0x22, 0x22, 0x22);
    private static final Color PIN_SHADOW = new Color(0, 0, 0, 70);
    private static final Color ROUTE_COLOR = new Color(0x44, 0x44, 0x44, 150);
    private static final BasicStroke ROUTE_STROKE = new BasicStroke(2f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND, 10f, new float[] {5f, 5f}, 0f);
    private static final int TILE_SIZE = WebMercator.TILE_SIZE;

    private final OsmTileClient tileClient;

    public OsmMapRenderer(OsmTileClient tileClient) {
        this.tileClient = tileClient;
    }

    /** A rendered map plus the points actually plotted (photos thinned, see {@link #thinPhotos}). */
    public record Rendered(BufferedImage image, MapPoints plotted) {}

    /**
     * Renders {@code points} onto a {@code width}x{@code height} PNG and saves it to
     * {@code target} (parent directories created on demand, an existing file overwritten), with
     * {@code text} of the plotted points as PNG {@code tEXt} chunks ({@link PngText}). Returns the
     * plotted points. Blocks (tile fetches): call off the FX thread.
     */
    public MapPoints render(MapPoints points, TileSource source, ZoomLevel zoom, int width, int height,
                            Function<MapPoints, Map<String, String>> text, Path target) {
        Rendered rendered = renderImage(points, source, zoom, width, height);
        try {
            Files.createDirectories(target.getParent());
            PngText.write(rendered.image(), target, text.apply(rendered.plotted()));
        } catch (IOException e) {
            throw new OsmTileClient.TileException("Could not save map to " + target, e);
        }
        return rendered.plotted();
    }

    /** Same as {@link #render}, returning the image without saving it — mainly for tests. */
    public Rendered renderImage(MapPoints points, TileSource source, ZoomLevel zoom, int width, int height) {
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

        // Zoom and centre come from the full point set; thinned pins sit under a kept one anyway.
        List<LatLon> photos = MapPoints.sampleEvenly(thinPhotos(points.dayPins(), points.photos(), z),
                Math.max(0, MAX_PINS - points.dayPinCount()));
        drawRoute(g, collapseRoute(points.route(), z), z, topLeftX, topLeftY);
        photos.forEach(p -> drawPin(g, p, z, topLeftX, topLeftY, PHOTO_PIN));
        points.starts().forEach(p -> drawPin(g, p, z, topLeftX, topLeftY, START_PIN));
        if (points.stop() != null) drawPin(g, points.stop(), z, topLeftX, topLeftY, STOP_PIN);

        drawAttribution(g, source, width, height);
        g.dispose();
        return new Rendered(image, points.withPhotosShown(photos));
    }

    /**
     * The photos that wouldn't be hidden under another pin at zoom {@code z} (todo 78), in their
     * order: a photo is dropped if its pin overlaps any kept pin — the day pins, always kept, or
     * an earlier kept photo. Distances are in world pixels, so the threshold is automatically a
     * zoom-dependent ground distance. Package-private for tests.
     */
    static List<LatLon> thinPhotos(List<LatLon> dayPins, List<LatLon> photos, int z) {
        double dayGap = PHOTO_PIN.outerRadius() + START_PIN.outerRadius();
        double photoGap = 2.0 * PHOTO_PIN.outerRadius();
        List<double[]> days = dayPins.stream().map(p -> pixel(p, z)).toList();
        List<double[]> keptPixels = new ArrayList<>();
        List<LatLon> kept = new ArrayList<>();
        for (LatLon photo : photos) {
            double[] px = pixel(photo, z);
            if (days.stream().anyMatch(d -> closerThan(px, d, dayGap))) continue;
            if (keptPixels.stream().anyMatch(k -> closerThan(px, k, photoGap))) continue;
            keptPixels.add(px);
            kept.add(photo);
        }
        return kept;
    }

    /**
     * The route without points closer than a photo pin's width to the <em>previous kept</em>
     * point — segments that small vanish under the pins anyway. Only consecutive points collapse,
     * so a later return to the same spot keeps its return leg. Package-private for tests.
     */
    static List<LatLon> collapseRoute(List<LatLon> route, int z) {
        double gap = 2.0 * PHOTO_PIN.outerRadius();
        List<LatLon> kept = new ArrayList<>();
        double[] last = null;
        for (LatLon p : route) {
            double[] px = pixel(p, z);
            if (last != null && closerThan(px, last, gap)) continue;
            kept.add(p);
            last = px;
        }
        return kept;
    }

    private static double[] pixel(LatLon p, int z) {
        return new double[] {WebMercator.pixelX(p.lon(), z), WebMercator.pixelY(p.lat(), z)};
    }

    private static boolean closerThan(double[] a, double[] b, double distance) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        return dx * dx + dy * dy < distance * distance;
    }

    /** A dashed grey line through the route, drawn under the pins (todo 78). */
    private static void drawRoute(Graphics2D g, List<LatLon> route, int z, double topLeftX, double topLeftY) {
        if (route.size() < 2) return;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < route.size(); i++) {
            double[] px = pixel(route.get(i), z);
            if (i == 0) path.moveTo(px[0] - topLeftX, px[1] - topLeftY);
            else path.lineTo(px[0] - topLeftX, px[1] - topLeftY);
        }
        var oldStroke = g.getStroke();
        g.setColor(ROUTE_COLOR);
        g.setStroke(ROUTE_STROKE);
        g.draw(path);
        g.setStroke(oldStroke);
    }

    /** package-private for tests. */
    static int fitZoom(List<LatLon> all, int width, int height, ZoomLevel zoom, int maxZoom) {
        boolean singlePoint = new HashSet<>(all).size() == 1;
        if (singlePoint) return Math.min(zoom.singlePointZoom, maxZoom);
        int base = WebMercator.fitZoom(all, width, height, BASE_MARGIN_FRACTION, maxZoom);
        return Math.max(0, Math.min(maxZoom, base + zoom.zoomBias));
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
    private static void drawPin(Graphics2D g, LatLon p, int z, double topLeftX, double topLeftY, PinStyle style) {
        int x = (int) Math.round(WebMercator.pixelX(p.lon(), z) - topLeftX);
        int y = (int) Math.round(WebMercator.pixelY(p.lat(), z) - topLeftY);
        int outer = style.outerRadius();
        int mid = style.radius() + 1;
        int r = style.radius();
        g.setColor(PIN_SHADOW);
        g.fillOval(x - outer + 1, y - outer + 2, outer * 2, outer * 2);
        g.setColor(PIN_OUTLINE);
        g.fillOval(x - outer, y - outer, outer * 2, outer * 2);
        g.setColor(Color.WHITE);
        g.fillOval(x - mid, y - mid, mid * 2, mid * 2);
        g.setColor(style.fill());
        g.fillOval(x - r, y - r, r * 2, r * 2);
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
