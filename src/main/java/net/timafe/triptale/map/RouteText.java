package net.timafe.triptale.map;

import net.timafe.triptale.domain.DiaryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The places of a range of entries as one line for copy and paste (todo 95): every day's
 * {@code start_place}, the last day's {@code stop_place}, consecutive duplicates collapsed,
 * a trailing {@code ", DE"} country code stripped, joined with an arrow. Days without a place
 * are skipped. A place that comes back later (a round trip) stays.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public final class RouteText {

    public static final String SEPARATOR = " → ";

    private static final Pattern COUNTRY = Pattern.compile(",\\s*[A-Z]{2}$");

    private RouteText() {
    }

    /** The route's places in order, ready for any exporter to format; entries are expected in date order. */
    public static List<String> places(List<DiaryEntry> entries) {
        List<String> places = new ArrayList<>();
        for (DiaryEntry e : entries) {
            add(places, e.startPlace());
        }
        if (!entries.isEmpty()) {
            add(places, entries.getLast().stopPlace());
        }
        return places;
    }

    public static String of(List<DiaryEntry> entries) {
        return of(entries, SEPARATOR);
    }

    /** Same as {@link #of(List)} with a caller-chosen separator, e.g. {@code ", "} or {@code " – "}. */
    public static String of(List<DiaryEntry> entries, String separator) {
        return String.join(separator, places(entries));
    }

    /** A place without its trailing {@code ", <ISO code>"}. */
    public static String stripCountry(String place) {
        return COUNTRY.matcher(place.strip()).replaceFirst("").strip();
    }

    private static void add(List<String> places, String raw) {
        if (raw == null || raw.isBlank()) return;
        String place = stripCountry(raw);
        if (place.isEmpty()) return;
        if (places.isEmpty() || !places.getLast().equals(place)) places.add(place);
    }
}
