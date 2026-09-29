package net.timafe.triptale.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
    private static final Pattern GOOGLE_MAPS_DIR = Pattern.compile("/maps/dir/([^?#]*)");
    private static final Pattern DIR_ORIGIN = Pattern.compile("[?&]origin=([^&#]+)");
    private static final Pattern DIR_DESTINATION = Pattern.compile("[?&]destination=([^&#]+)");
    private static final Pattern LAT_LON_PAIR = Pattern.compile(
            "(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)");
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

    /** Degrees and whole minutes, e.g. {@code 51\u00b029\u2032N 0\u00b001\u2032W}. */
    public static String toDdm(double lat, double lon) {
        return ddmComponent(lat, 'N', 'S') + " " + ddmComponent(lon, 'E', 'W');
    }

    private static String ddmComponent(double value, char positive, char negative) {
        char hemisphere = value < 0 ? negative : positive;
        long totalMinutes = Math.round(Math.abs(value) * 60.0);
        return String.format(Locale.ROOT, "%d\u00b0%02d\u2032%s", totalMinutes / 60, totalMinutes % 60, hemisphere);
    }

    /**
     * Google Maps link: a pin on {@code start}, or directions from {@code start} to {@code stop}
     * when both are set. No travel mode — Google picks one and the user can switch.
     */
    public static String googleMapsUrl(LatLon start, LatLon stop) {
        if (stop == null) return "https://www.google.com/maps?q=" + start.lat() + "," + start.lon();
        return "https://www.google.com/maps/dir/?api=1&origin=" + start.lat() + "," + start.lon()
                + "&destination=" + stop.lat() + "," + stop.lon();
    }

    /**
     * Tries to extract a lat/lon pair from free text, in this order: GPX {@code <trkpt>}
     * attributes, Google Maps directions URL ({@code /maps/dir/A/B/...}), Google Maps URL
     * ({@code @lat,lon[,zoom]} or {@code ?q=lat,lon}), GeoJSON {@code [lon, lat]} array. When
     * the text holds several points (GPX track, directions), the first one wins — see
     * {@link #tryParseLast}. Returns empty if no format matches or the matched values are out
     * of range.
     */
    public static Optional<LatLon> tryParse(String text) {
        return parse(text, false);
    }

    /** Like {@link #tryParse}, but takes the last of several points: a track's or route's end. */
    public static Optional<LatLon> tryParseLast(String text) {
        return parse(text, true);
    }

    private static Optional<LatLon> parse(String text, boolean last) {
        if (text == null || text.isBlank()) return Optional.empty();

        Matcher gpx = GPX_TRKPT.matcher(text);
        if (gpx.find()) {
            String lat = gpx.group(1);
            String lon = gpx.group(2);
            while (last && gpx.find()) {
                lat = gpx.group(1);
                lon = gpx.group(2);
            }
            return toLatLon(Double.parseDouble(lat), Double.parseDouble(lon));
        }
        // Checked before GOOGLE_MAPS_AT: in a directions URL the @lat,lon is the map view's
        // centre, not one of the stops.
        Matcher mapsDir = GOOGLE_MAPS_DIR.matcher(text);
        if (mapsDir.find()) {
            Matcher param = (last ? DIR_DESTINATION : DIR_ORIGIN).matcher(text);
            return param.find() ? latLonPair(param.group(1)) : directionsStop(mapsDir.group(1), last);
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

    /**
     * First or last stop of a directions path like {@code 51.47,8.33/Werl/@51.49,8.1,11z/data=...}
     * (as copied from the address bar; the {@code ?api=1&origin=..&destination=..} form is
     * handled by the caller).
     * Empty when that stop is a place name rather than coordinates — falling back to another
     * stop would silently fill in the wrong point.
     */
    private static Optional<LatLon> directionsStop(String path, boolean last) {
        List<String> stops = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.startsWith("@") || segment.startsWith("data=")) break;
            stops.add(segment);
        }
        if (stops.isEmpty()) return Optional.empty();
        return latLonPair(stops.get(last ? stops.size() - 1 : 0));
    }

    /** A URL-encoded {@code lat,lon} (optionally with a space after the comma), else empty. */
    private static Optional<LatLon> latLonPair(String encoded) {
        Matcher pair = LAT_LON_PAIR.matcher(urlDecode(encoded));
        if (!pair.matches()) return Optional.empty();
        return toLatLon(Double.parseDouble(pair.group(1)), Double.parseDouble(pair.group(2)));
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    private static Optional<LatLon> toLatLon(double lat, double lon) {
        return isValid(lat, lon) ? Optional.of(new LatLon(lat, lon)) : Optional.empty();
    }
}
