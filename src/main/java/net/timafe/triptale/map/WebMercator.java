package net.timafe.triptale.map;

import net.timafe.triptale.util.Coordinates.LatLon;

import java.util.List;

/**
 * Web Mercator slippy-map tile math for {@link OsmMapRenderer} (todo 61): the same projection
 * every OSM-family tile server uses. Pure math, no I/O — easy to unit test.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public final class WebMercator {

    public static final int TILE_SIZE = 256;

    private WebMercator() {}

    /** How many tiles make up one side of the world map at {@code zoom} (2^zoom). */
    public static double tilesPerSide(int zoom) {
        return Math.pow(2, zoom);
    }

    /** The world-pixel X coordinate of {@code lon} at {@code zoom} (0 at -180°, grows eastward). */
    public static double pixelX(double lon, int zoom) {
        return (lon + 180.0) / 360.0 * tilesPerSide(zoom) * TILE_SIZE;
    }

    /** The world-pixel Y coordinate of {@code lat} at {@code zoom} (0 at the north pole, grows southward). */
    public static double pixelY(double lat, int zoom) {
        double sin = Math.sin(Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, lat))));
        double y = 0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI);
        return y * tilesPerSide(zoom) * TILE_SIZE;
    }

    /**
     * Pixels reserved on every side of the image for the margin around the plotted points, like
     * Mapbox's old {@code padding}: a fraction of the smallest side, capped so it never reaches
     * half of either side.
     */
    public static int marginPixels(int width, int height, double marginFraction) {
        int smallestSide = Math.min(width, height);
        int margin = (int) Math.round(smallestSide * marginFraction);
        int cap = smallestSide / 2 - 1;
        return Math.max(0, Math.min(margin, cap));
    }

    /**
     * The highest zoom (capped at {@code maxZoom}) where the bounding box of {@code points} fits
     * {@code width}x{@code height} minus the margin on every side; 0 if even the whole world
     * view at zoom 0 doesn't fit (shouldn't happen at any sane image size). Doesn't handle a
     * bounding box crossing the antimeridian (180°/-180° longitude) specially — a rare case for
     * a diary, left as a known limitation.
     */
    public static int fitZoom(List<LatLon> points, int width, int height, double marginFraction, int maxZoom) {
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        for (LatLon p : points) {
            minLat = Math.min(minLat, p.lat());
            maxLat = Math.max(maxLat, p.lat());
            minLon = Math.min(minLon, p.lon());
            maxLon = Math.max(maxLon, p.lon());
        }
        int margin = marginPixels(width, height, marginFraction);
        double availableWidth = width - 2.0 * margin;
        double availableHeight = height - 2.0 * margin;
        for (int z = maxZoom; z > 0; z--) {
            double w = pixelX(maxLon, z) - pixelX(minLon, z);
            double h = pixelY(minLat, z) - pixelY(maxLat, z);
            if (w <= availableWidth && h <= availableHeight) return z;
        }
        return 0;
    }
}
