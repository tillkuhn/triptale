package net.timafe.triptale.ui;

import net.timafe.triptale.map.MapPoints;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.util.Coordinates.LatLon;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Builds Mapbox Static Images API URLs for Map Assistant's {@link MapPoints}: red start pins, a
 * teal stop pin and yellow camera pins for photo locations.
 * <p>
 * The access token comes from {@code settings.yml} ({@link net.timafe.triptale.config.AppSettings#getMapboxToken()},
 * editable via the Edit Settings dialog) and is re-read on every call, same as the impressions
 * patterns. Must be an unrestricted (no URL/referrer restriction) public token — a restricted
 * one gets rejected with 403 Forbidden since neither curl nor JavaFX's image loader sends a
 * matching {@code Referer}.
 */
public final class MapboxService {

    /**
     * Pins that fit in one request: Mapbox rejects URLs over 8192 chars, and a camera pin takes
     * about 45 of them, which leaves room for the token and the rest of the URL.
     */
    public static final int MAX_PINS = 150;

    private static final String STYLE = "mapbox/streets-v12";
    private static final String START_PIN = "pin-s+f74e4e";
    private static final String STOP_PIN = "pin-s+3bb2d0";
    /** Maki's "attraction" icon is the camera glyph; there is no "camera" symbol (HTTP 422). */
    private static final String PHOTO_PIN = "pin-s-attraction+f5c242";

    /**
     * How far Sgt. Mapper zooms out around the plotted pins. Mapbox's own {@code auto} default
     * padding is capped at 12px per side, which crowds pins against the edge of the image; these
     * levels instead request an explicit {@code padding} proportional to the image size (for
     * multiple points) or an explicit {@code zoom} (for a single point, where {@code auto} would
     * zoom all the way in to building level).
     */
    public enum ZoomLevel {
        CLOSE("Close", 0.02, 16),
        NORMAL("Normal", 0.12, 12),
        WIDE("Wide", 0.28, 9),
        VERY_WIDE("Very wide", 0.45, 5);

        private final String label;
        private final double paddingFraction;
        private final int singlePointZoom;

        ZoomLevel(String label, double paddingFraction, int singlePointZoom) {
            this.label = label;
            this.paddingFraction = paddingFraction;
            this.singlePointZoom = singlePointZoom;
        }

        @Override public String toString() {
            return label;
        }
    }

    public static final ZoomLevel DEFAULT_ZOOM = ZoomLevel.NORMAL;

    private final SettingsStore settingsStore;

    public MapboxService(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    public boolean hasToken() {
        String token = settingsStore.load().getMapboxToken();
        return token != null && !token.isBlank();
    }

    /** Same as {@link #staticImageUrl(MapPoints, int, int, ZoomLevel)} with {@link #DEFAULT_ZOOM}. */
    public String staticImageUrl(MapPoints points, int width, int height) {
        return staticImageUrl(points, width, height, DEFAULT_ZOOM);
    }

    /** Photos first, then starts, then the stop, so the day pins are drawn on top. */
    public String staticImageUrl(MapPoints points, int width, int height, ZoomLevel zoom) {
        String token = settingsStore.load().getMapboxToken();
        if (token == null || token.isBlank()) throw new IllegalStateException("No Mapbox token configured");
        if (points.isEmpty()) throw new IllegalArgumentException("No points to plot");
        if (zoom == null) zoom = DEFAULT_ZOOM;

        List<String> pinParts = new ArrayList<>();
        points.photos().forEach(p -> pinParts.add(pin(PHOTO_PIN, p)));
        points.starts().forEach(p -> pinParts.add(pin(START_PIN, p)));
        if (points.stop() != null) pinParts.add(pin(STOP_PIN, points.stop()));
        String pins = String.join(",", pinParts);

        List<LatLon> all = points.all();
        boolean singlePoint = new HashSet<>(all).size() == 1;
        String viewport = singlePoint
                ? String.format(Locale.ROOT, "%.5f,%.5f,%d", all.getFirst().lon(), all.getFirst().lat(), zoom.singlePointZoom)
                : "auto";
        // padding only applies to auto/bbox viewports, and must not exceed the image's own size
        String padding = singlePoint ? "" : "&padding=" + autoPadding(width, height, zoom);

        return "https://api.mapbox.com/styles/v1/" + STYLE + "/static/" + pins
                + "/" + viewport + "/" + width + "x" + height + "@2x"
                + "?access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + padding;
    }

    private static int autoPadding(int width, int height, ZoomLevel zoom) {
        int smallestSide = Math.min(width, height);
        int padding = (int) Math.round(smallestSide * zoom.paddingFraction);
        // Mapbox rejects padding that reaches half of either side of the image
        int cap = smallestSide / 2 - 1;
        return Math.max(1, Math.min(padding, cap));
    }

    /** {@code text} (e.g. an image loader error quoting the URL) with the access token masked. */
    public static String redact(String text) {
        return text == null ? null : text.replaceAll("access_token=[^&\\s]+", "access_token=***");
    }

    private static String pin(String pin, LatLon p) {
        return String.format(Locale.ROOT, "%s(%.5f,%.5f)", pin, p.lon(), p.lat());
    }
}
