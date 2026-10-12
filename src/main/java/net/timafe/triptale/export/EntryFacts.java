package net.timafe.triptale.export;

import net.timafe.triptale.domain.Activity;
import net.timafe.triptale.domain.DiaryEntry;
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
 * climb, and the start/stop point. Items without data drop out, so there are 0–3 of them. Each
 * exporter lays them out its own way: a byline in Markdown, a "ticket stub" strip in HTML,
 * columns on WordPress.
 */
record EntryFacts(List<Item> items) {

    /** Shown when there is distance or climb but no activity. */
    static final String NO_ACTIVITY_EMOJI = "📏";
    static final String LOCATION_EMOJI = "🧭";
    static final String DATE_EMOJI = "📅";

    /**
     * One fact: an emoji, or for the date a day {@code stamp} ("Tag", "3"); a bold value; an
     * optional muted note; and {@code inline}, the whole fact on one line for the byline.
     */
    record Item(String emoji, Stamp stamp, String value, String note, String inline) {}

    /** The day number badge, e.g. "Tag" over "3". */
    record Stamp(String label, long day) {}

    static EntryFacts of(LocalDate tripStart, DiaryEntry e, ExportTexts texts) {
        List<Item> items = new ArrayList<>();
        items.add(date(tripStart, e.date(), texts));
        activity(e, texts).ifPresent(items::add);
        location(e, texts).ifPresent(items::add);
        return new EntryFacts(List.copyOf(items));
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
        return new Item(DATE_EMOJI, stamp, weekday, longDate, inline);
    }

    /** Distance and climb led by the activity emoji; an activity alone shows its name. Zero values count as absent (train days). */
    private static Optional<Item> activity(DiaryEntry e, ExportTexts texts) {
        String activity = Activity.normalize(e.activity());
        String distance = e.distance() != null && e.distance() > 0 ? texts.distance(e.distance()) + " km" : null;
        String climb = e.altitudeMeters() != null && e.altitudeMeters() > 0
                ? texts.altitude(e.altitudeMeters()) + " m " + texts.word("facts.climbed") : null;
        if (distance != null || climb != null) {
            String emoji = activity == null ? NO_ACTIVITY_EMOJI : Activity.emojiFor(activity);
            String value = distance != null ? distance : climb;
            String note = distance != null ? climb : null;
            String inline = emoji + " " + Stream.of(distance, climb).filter(Objects::nonNull).collect(Collectors.joining(", "));
            return Optional.of(new Item(emoji, null, value, note, inline));
        }
        if (activity == null) return Optional.empty();
        return Optional.of(new Item(Activity.emojiFor(activity), null, texts.activityLabel(activity), null,
                texts.activity(activity)));
    }

    /** Start-focused: the start point with "Starting point" below, or "→ stop" below when there is one. */
    private static Optional<Item> location(DiaryEntry e, ExportTexts texts) {
        String start = e.startLat() != null && e.startLon() != null ? Coordinates.toDdm(e.startLat(), e.startLon()) : null;
        String stop = e.stopLat() != null && e.stopLon() != null ? "→ " + Coordinates.toDdm(e.stopLat(), e.stopLon()) : null;
        if (start == null && stop == null) return Optional.empty();
        String inline = LOCATION_EMOJI + " " + Stream.of(start, stop).filter(Objects::nonNull).collect(Collectors.joining(" "));
        if (start == null) {
            return Optional.of(new Item(LOCATION_EMOJI, null, stop, texts.word("facts.stop"), inline));
        }
        return Optional.of(new Item(LOCATION_EMOJI, null, start,
                stop != null ? stop : texts.word("facts.start"), inline));
    }
}
