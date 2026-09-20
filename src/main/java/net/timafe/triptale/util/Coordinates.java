package net.timafe.triptale.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Coordinates {

    public record LatLon(double lat, double lon) {}

    private static final double MAX_LAT = 90.0;
    private static final double MAX_LON = 180.0;

    private static final Pattern GPX_TRKPT = Pattern.compile(
            "lat=\"(-?\\d+(?:\\.\\d+)?)\"\\s+lon=\"(-?\\d+(?:\\.\\d+)?)\"");
    private static final Pattern GOOGLE_MAPS_AT = Pattern.compile(
            "@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:,[\\d.]+z)?");
    private static final Pattern GOOGLE_MAPS_QUERY = Pattern.compile(
            "[?&]q=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern GEOJSON_ARRAY = Pattern.compile(
            "\\[\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*]");

    private Coordinates() {}

    public static boolean isValid(double lat, double lon) {
        return lat >= -MAX_LAT && lat <= MAX_LAT && lon >= -MAX_LON && lon <= MAX_LON;
    }

    /** Degrees-decimal-minutes, e.g. {@code 51° 29.3' N 0° 0.8' W}. */
    public static String toDdm(double lat, double lon) {
        return ddmComponent(lat, 'N', 'S') + " " + ddmComponent(lon, 'E', 'W');
    }

    private static String ddmComponent(double value, char positive, char negative) {
        char hemisphere = value < 0 ? negative : positive;
        double abs = Math.abs(value);
        int deg = (int) abs;
        double minutes = Math.round((abs - deg) * 60.0 * 10.0) / 10.0;
        if (minutes >= 60.0) {
            deg += 1;
            minutes -= 60.0;
        }
        return String.format(Locale.ROOT, "%d\u00b0 %.1f' %s", deg, minutes, hemisphere);
    }

    /**
     * Tries to extract a lat/lon pair from free text, in this order: GPX {@code <trkpt>}
     * attributes, Google Maps URL ({@code @lat,lon[,zoom]} or {@code ?q=lat,lon}), GeoJSON
     * {@code [lon, lat]} array. Returns empty if no format matches or the matched values are
     * out of range.
     */
    public static Optional<LatLon> tryParse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();

        Matcher gpx = GPX_TRKPT.matcher(text);
        if (gpx.find()) {
            return toLatLon(Double.parseDouble(gpx.group(1)), Double.parseDouble(gpx.group(2)));
        }
        Matcher mapsAt = GOOGLE_MAPS_AT.matcher(text);
        if (mapsAt.find()) {
            return toLatLon(Double.parseDouble(mapsAt.group(1)), Double.parseDouble(mapsAt.group(2)));
        }
        Matcher mapsQuery = GOOGLE_MAPS_QUERY.matcher(text);
        if (mapsQuery.find()) {
            return toLatLon(Double.parseDouble(mapsQuery.group(1)), Double.parseDouble(mapsQuery.group(2)));
        }
        Matcher geoJson = GEOJSON_ARRAY.matcher(text);
        if (geoJson.find()) {
            double lon = Double.parseDouble(geoJson.group(1));
            double lat = Double.parseDouble(geoJson.group(2));
            return toLatLon(lat, lon);
        }
        return Optional.empty();
    }

    private static Optional<LatLon> toLatLon(double lat, double lon) {
        return isValid(lat, lon) ? Optional.of(new LatLon(lat, lon)) : Optional.empty();
    }
}
