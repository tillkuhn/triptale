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
    private static final String PHOTO_PIN = "pin-s-camera+f5c242";
    /** Zoom used for a single point, where "auto" bbox-fitting would zoom in to building level. */
    private static final int SINGLE_POINT_ZOOM = 12;

    private final SettingsStore settingsStore;

    public MapboxService(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    public boolean hasToken() {
        String token = settingsStore.load().getMapboxToken();
        return token != null && !token.isBlank();
    }

    /** Photos first, then starts, then the stop, so the day pins are drawn on top. */
    public String staticImageUrl(MapPoints points, int width, int height) {
        String token = settingsStore.load().getMapboxToken();
        if (token == null || token.isBlank()) throw new IllegalStateException("No Mapbox token configured");
        if (points.isEmpty()) throw new IllegalArgumentException("No points to plot");

        List<String> pinParts = new ArrayList<>();
        points.photos().forEach(p -> pinParts.add(pin(PHOTO_PIN, p)));
        points.starts().forEach(p -> pinParts.add(pin(START_PIN, p)));
        if (points.stop() != null) pinParts.add(pin(STOP_PIN, points.stop()));
        String pins = String.join(",", pinParts);

        List<LatLon> all = points.all();
        String viewport = new HashSet<>(all).size() == 1
                ? String.format(Locale.ROOT, "%.5f,%.5f,%d", all.getFirst().lon(), all.getFirst().lat(), SINGLE_POINT_ZOOM)
                : "auto";

        return "https://api.mapbox.com/styles/v1/" + STYLE + "/static/" + pins
                + "/" + viewport + "/" + width + "x" + height + "@2x"
                + "?access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String pin(String pin, LatLon p) {
        return String.format(Locale.ROOT, "%s(%.5f,%.5f)", pin, p.lon(), p.lat());
    }
}
