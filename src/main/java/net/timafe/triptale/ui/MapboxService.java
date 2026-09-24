package net.timafe.triptale.ui;

import net.timafe.triptale.storage.SettingsStore;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds Mapbox Static Images API URLs for a trip's start points, plus its latest stop point.
 * <p>
 * The access token comes from {@code settings.yml} ({@link net.timafe.triptale.config.AppSettings#getMapboxToken()},
 * editable via the Edit Settings dialog) and is re-read on every call, same as the impressions
 * patterns. Must be an unrestricted (no URL/referrer restriction) public token — a restricted
 * one gets rejected with 403 Forbidden since neither curl nor JavaFX's image loader sends a
 * matching {@code Referer}.
 */
public final class MapboxService {

    private static final String STYLE = "mapbox/streets-v12";
    private static final String START_PIN_COLOR = "f74e4e";
    private static final String END_PIN_COLOR = "3bb2d0";
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

    /**
     * {@code startPoints} entries are {@code {lon, lat}}, one pin each; {@code endPoint} (also
     * {@code {lon, lat}}, or {@code null}) gets a single differently-colored pin — callers pass
     * at most the latest entry's stop coordinates here, not one per day, since a multi-day trip's
     * daily stop is usually the next day's start (redundant), while a one-way trip's final
     * destination otherwise never appears on the map at all.
     */
    public String staticImageUrl(List<double[]> startPoints, double[] endPoint, int width, int height) {
        String token = settingsStore.load().getMapboxToken();
        if (token == null || token.isBlank()) throw new IllegalStateException("No Mapbox token configured");
        if (startPoints.isEmpty() && endPoint == null) throw new IllegalArgumentException("No points to plot");

        List<String> pinParts = new ArrayList<>(startPoints.stream()
                .map(p -> pin(START_PIN_COLOR, p))
                .toList());
        if (endPoint != null) pinParts.add(pin(END_PIN_COLOR, endPoint));
        String pins = String.join(",", pinParts);

        int totalPoints = startPoints.size() + (endPoint != null ? 1 : 0);
        double[] onlyPoint = totalPoints == 1 ? (startPoints.isEmpty() ? endPoint : startPoints.get(0)) : null;
        String viewport = onlyPoint != null
                ? String.format(Locale.ROOT, "%f,%f,%d", onlyPoint[0], onlyPoint[1], SINGLE_POINT_ZOOM)
                : "auto";

        return "https://api.mapbox.com/styles/v1/" + STYLE + "/static/" + pins
                + "/" + viewport + "/" + width + "x" + height + "@2x"
                + "?access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String pin(String color, double[] lonLat) {
        return String.format(Locale.ROOT, "pin-s+%s(%f,%f)", color, lonLat[0], lonLat[1]);
    }
}
