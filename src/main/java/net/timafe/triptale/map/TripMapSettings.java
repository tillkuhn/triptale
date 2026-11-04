package net.timafe.triptale.map;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What a Sgt. Mapper render was made with (todo 82, D1): the entry range, tile style, zoom,
 * format, whether photo locations and the route line (todo 78) were included, plus how many points it shows. Stored as
 * PNG {@code tEXt} chunks ({@link PngText}) in every render, so a saved {@code tripmap.png}
 * restores the dialog's controls when it's opened again.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public record TripMapSettings(LocalDate from, LocalDate to, TileSource source, OsmMapRenderer.ZoomLevel zoom,
                              MapFormat format, boolean photos, boolean route, int starts, boolean stop, int photosShown,
                              int photosFound) {

    static final String PREFIX = "triptale:";

    /** The settings plus the point counts of {@code points}, as plotted. */
    public static TripMapSettings of(LocalDate from, LocalDate to, TileSource source, OsmMapRenderer.ZoomLevel zoom,
                                     MapFormat format, boolean photos, boolean route, MapPoints points) {
        return new TripMapSettings(from, to, source, zoom, format, photos, route, points.starts().size(),
                points.stop() != null, points.photos().size(), points.photosFound());
    }

    /** Keyword/value pairs for {@link PngText#write}, in a stable order. */
    public Map<String, String> toText() {
        Map<String, String> text = new LinkedHashMap<>();
        text.put(PREFIX + "from", from.toString());
        text.put(PREFIX + "to", to.toString());
        text.put(PREFIX + "style", source.id());
        text.put(PREFIX + "zoom", zoom.name());
        text.put(PREFIX + "format", format.name());
        text.put(PREFIX + "photos", Boolean.toString(photos));
        text.put(PREFIX + "route", Boolean.toString(route));
        text.put(PREFIX + "starts", Integer.toString(starts));
        text.put(PREFIX + "stop", Boolean.toString(stop));
        text.put(PREFIX + "photosShown", Integer.toString(photosShown));
        text.put(PREFIX + "photosFound", Integer.toString(photosFound));
        return text;
    }

    /**
     * Parses what {@link #toText()} wrote. Empty if the range is missing or unreadable — without
     * it the map can't be matched to the entries. An unknown style, zoom or format (one removed
     * since) falls back to the default, so an old map still loads.
     */
    public static Optional<TripMapSettings> fromText(Map<String, String> text) {
        try {
            LocalDate from = LocalDate.parse(text.getOrDefault(PREFIX + "from", ""));
            LocalDate to = LocalDate.parse(text.getOrDefault(PREFIX + "to", ""));
            if (to.isBefore(from)) return Optional.empty();
            String style = text.get(PREFIX + "style");
            TileSource source = Arrays.stream(TileSource.values()).filter(s -> s.id().equals(style))
                    .findFirst().orElse(TileSource.OPEN_TOPO_MAP);
            return Optional.of(new TripMapSettings(from, to, source,
                    enumOr(OsmMapRenderer.ZoomLevel.class, text.get(PREFIX + "zoom"), OsmMapRenderer.DEFAULT_ZOOM),
                    enumOr(MapFormat.class, text.get(PREFIX + "format"), MapFormat.DEFAULT),
                    Boolean.parseBoolean(text.get(PREFIX + "photos")),
                    Boolean.parseBoolean(text.get(PREFIX + "route")),
                    intOr(text.get(PREFIX + "starts")),
                    Boolean.parseBoolean(text.get(PREFIX + "stop")),
                    intOr(text.get(PREFIX + "photosShown")),
                    intOr(text.get(PREFIX + "photosFound"))));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static int intOr(String value) {
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
