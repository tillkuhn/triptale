package net.timafe.triptale.export;

import net.timafe.triptale.domain.Activity;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Weather;
import net.timafe.triptale.domain.WeatherCode;
import net.timafe.triptale.i18n.ExportTexts;
import net.timafe.triptale.util.Coordinates;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The key facts shown under an entry's title (todo 53): the day, the activity with distance and
 * climb, and the start/stop point, by place name where there is one (todo 58). Items without data drop out, so there are 0–3 of them. Each
 * exporter lays them out its own way: a byline in Markdown, a "ticket stub" strip in HTML,
 * columns on WordPress.
 */
record EntryFacts(List<Item> items) {

    /** Shown when there is distance or climb but no activity. */
    static final String NO_ACTIVITY_EMOJI = "📏";
    static final String LOCATION_EMOJI = "🧭";
    /** The spiral calendar, not 📅: that one shows a fixed date ("JUL 17"), which clashes with the real one. */
    static final String DATE_EMOJI = "🗓\uFE0F";

    /**
     * One fact: an emoji, or for the date a day {@code stamp} ("Tag", "3"); a bold value; an
     * optional muted note; an optional smaller {@code detail} (the coordinates behind place
     * names); and {@code inline}, the whole fact on one line for the byline.
     */
    record Item(String emoji, Stamp stamp, String value, String note, String detail, String inline) {}

    /** The day number badge, e.g. "Tag" over "3". */
    record Stamp(String label, long day) {}

    static EntryFacts of(LocalDate tripStart, DiaryEntry e, ExportTexts texts) {
        List<Item> items = new ArrayList<>();
        items.add(date(tripStart, e.date(), texts));
        activity(e, texts).ifPresent(items::add);
        weather(e, texts).ifPresent(items::add);
        location(e, texts).ifPresent(items::add);
        return new EntryFacts(List.copyOf(items));
    }

    /** Whether the entry names a start or stop place, i.e. the export needs the OpenStreetMap credit. */
    static boolean hasPlace(DiaryEntry e) {
        return e.startPlace() != null || e.stopPlace() != null;
    }

    /** Day N relative to the trip start; null without a start date. */
    static Long dayNumber(LocalDate tripStart, LocalDate date) {
        return tripStart == null ? null : ChronoUnit.DAYS.between(tripStart, date) + 1;
    }

    /** All facts on one line, e.g. "Mittwoch, 23. Juli 2026 · Tag 3 · 🚴 123,0 km, 100 m bergauf". */
    String byline() {
        return items.stream().map(Item::inline).collect(Collectors.joining(" · "));
    }

    /** The ticket stub strip, styled by {@code div.facts} in {@code html-shell.html}; no blank lines, so Markdown keeps it as one HTML block. */
    String html() {
        StringBuilder sb = new StringBuilder("<div class=\"facts\">\n");
        for (Item item : items) {
            sb.append("<div class=\"fact\">");
            if (item.stamp() != null) {
                sb.append("<span class=\"fact-stamp\"><small>").append(DiaryExporter.escapeHtml(item.stamp().label()))
                        .append("</small>").append(item.stamp().day()).append("</span>");
            } else {
                sb.append("<span class=\"fact-emoji\">").append(item.emoji()).append("</span>");
            }
            sb.append("<span class=\"fact-value\">").append(DiaryExporter.escapeHtml(item.value())).append("</span>");
            if (item.note() != null) {
                sb.append("<span class=\"fact-note\">").append(DiaryExporter.escapeHtml(item.note())).append("</span>");
            }
            if (item.detail() != null) {
                sb.append("<span class=\"fact-detail\">").append(DiaryExporter.escapeHtml(item.detail())).append("</span>");
            }
            sb.append("</div>\n");
        }
        return sb.append("</div>").toString();
    }

    private static Item date(LocalDate tripStart, LocalDate date, ExportTexts texts) {
        Long day = dayNumber(tripStart, date);
        String weekday = texts.weekday(date);
        String longDate = texts.longDate(date);
        String inline = weekday + ", " + longDate + (day == null ? "" : " · " + texts.word("day") + " " + day);
        Stamp stamp = day == null ? null : new Stamp(texts.word("day"), day);
        return new Item(DATE_EMOJI, stamp, weekday, longDate, null, inline);
    }

    /**
     * The activity's name leads when there is one, with distance/climb below it; without an
     * activity, distance leads instead (climb below it, if any). Zero values count as absent (train days).
     */
    private static Optional<Item> activity(DiaryEntry e, ExportTexts texts) {
        String activity = Activity.normalize(e.activity());
        String distance = e.distance() != null && e.distance() > 0 ? texts.distance(e.distance()) + " km" : null;
        String climb = e.altitudeMeters() != null && e.altitudeMeters() > 0
                ? texts.altitude(e.altitudeMeters()) + " m " + texts.word("facts.climbed") : null;
        String distanceClimb = Stream.of(distance, climb).filter(Objects::nonNull).collect(Collectors.joining(", "));
        if (activity == null && distanceClimb.isEmpty()) return Optional.empty();
        String emoji = activity == null ? NO_ACTIVITY_EMOJI : Activity.emojiFor(activity);
        if (activity != null) {
            String value = texts.activityLabel(activity);
            String note = distanceClimb.isEmpty() ? null : distanceClimb;
            String inline = texts.activity(activity) + (distanceClimb.isEmpty() ? "" : ", " + distanceClimb);
            return Optional.of(new Item(emoji, null, value, note, null, inline));
        }
        String value = distance != null ? distance : climb;
        String note = distance != null ? climb : null;
        String inline = emoji + " " + distanceClimb;
        return Optional.of(new Item(emoji, null, value, note, null, inline));
    }

    /** Min–max temperature (just one value when they're equal) and the WMO code's bucket label. */
    private static Optional<Item> weather(DiaryEntry e, ExportTexts texts) {
        Weather w = e.weather();
        if (w == null) return Optional.empty();
        String emoji = WeatherCode.fromWmo(w.code()).emoji();
        String value = w.tempMin() == w.tempMax() ? w.tempMin() + "°" : w.tempMin() + "–" + w.tempMax() + "°";
        String note = texts.weatherLabel(w.code());
        String inline = emoji + " " + value + " " + note;
        return Optional.of(new Item(emoji, null, value, note, null, inline));
    }

    /**
     * Start-focused: the start point, with its own coordinates directly below it when it has a
     * place name, and "→ stop" as the smallest line below that — the stop point never shows its
     * own coordinates once there's also a start point (todo 58 follow-up: the pair of coordinate
     * sets read as redundant once one of the two points already names a place). A lone start or
     * stop point (no counterpart) keeps the older "Starting point"/"Destination" label instead,
     * with its own coordinates as the detail line if it has a place name.
     */
    private static Optional<Item> location(DiaryEntry e, ExportTexts texts) {
        String startDdm = e.startLat() != null && e.startLon() != null ? Coordinates.toDdm(e.startLat(), e.startLon()) : null;
        String stopDdm = e.stopLat() != null && e.stopLon() != null ? Coordinates.toDdm(e.stopLat(), e.stopLon()) : null;
        String start = e.startPlace() != null ? e.startPlace() : startDdm;
        String stop = e.stopPlace() != null ? e.stopPlace() : stopDdm;
        if (start == null && stop == null) return Optional.empty();
        String arrowStop = stop == null ? null : "→ " + stop;
        String inline = LOCATION_EMOJI + " " + Stream.of(start, arrowStop).filter(Objects::nonNull).collect(Collectors.joining(" "));
        // Only coordinates that a place name hides; a point shown by its coordinates already has them
        String startDetail = e.startPlace() != null ? startDdm : null;
        if (start == null) {
            return Optional.of(new Item(LOCATION_EMOJI, null, arrowStop, texts.word("facts.stop"), null, inline));
        }
        if (arrowStop == null) {
            return Optional.of(new Item(LOCATION_EMOJI, null, start, texts.word("facts.start"), startDetail, inline));
        }
        return Optional.of(new Item(LOCATION_EMOJI, null, start, startDetail, arrowStop, inline));
    }
}
