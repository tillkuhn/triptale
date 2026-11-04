package net.timafe.triptale.map;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sgt. Mapper's option row (todo 78): tile style, zoom, format and the two checkboxes. Remembered
 * across sessions as {@code lastMapOptions} in state.yml ({@code MarkdownStore.saveLastMapOptions}),
 * so the dialog opens the way it was last closed; a saved map's own {@link TripMapSettings} still
 * win when that map is shown.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public record MapOptions(TileSource source, OsmMapRenderer.ZoomLevel zoom, MapFormat format,
                         boolean photos, boolean route) {

    /** What a first-time user gets: photos on, route line off. */
    public static final MapOptions DEFAULT = new MapOptions(TileSource.OPEN_TOPO_MAP, OsmMapRenderer.DEFAULT_ZOOM,
            MapFormat.DEFAULT, true, false);

    /** Flat string map for state.yml, in a stable order. */
    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("style", source.id());
        map.put("zoom", zoom.name());
        map.put("format", format.name());
        map.put("photos", Boolean.toString(photos));
        map.put("route", Boolean.toString(route));
        return map;
    }

    /** Parses {@link #toMap()}; every missing or unknown value falls back to {@link #DEFAULT}'s. */
    public static MapOptions fromMap(Map<String, String> map) {
        String style = map.get("style");
        return new MapOptions(
                Arrays.stream(TileSource.values()).filter(s -> s.id().equals(style)).findFirst().orElse(DEFAULT.source),
                enumOr(OsmMapRenderer.ZoomLevel.class, map.get("zoom"), DEFAULT.zoom),
                enumOr(MapFormat.class, map.get("format"), DEFAULT.format),
                boolOr(map.get("photos"), DEFAULT.photos),
                boolOr(map.get("route"), DEFAULT.route));
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static boolean boolOr(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }
}
