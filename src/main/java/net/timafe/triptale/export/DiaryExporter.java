package net.timafe.triptale.export;

import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.ImpressionsResolver;
import net.timafe.triptale.storage.MarkdownStore;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiaryExporter {

    private static final String SHELL = "/export/diary-template.md";
    private static final String ENTRY_HEADING = "/export/entry-heading.md";
    private static final String ENTRY_DISTANCE = "/export/entry-distance.md";
    private static final String ENTRY_ALTITUDE = "/export/entry-altitude.md";
    private static final String ENTRY_TRACK = "/export/entry-track.md";
    private static final String ENTRY_TALES = "/export/entry-tales.md";
    private static final String HTML_SHELL = "/export/html-shell.html";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
    private static final String MISSING = "—";
    private static final Pattern IMPRESSIONS_MARKER = Pattern.compile("<!--IMPRESSIONS:(\\d{4}-\\d{2}-\\d{2})-->");

    private final MarkdownStore store;
    private final ImpressionsResolver impressionsResolver;

    public DiaryExporter(MarkdownStore store, ImpressionsResolver impressionsResolver) {
        this.store = store;
        this.impressionsResolver = impressionsResolver;
    }

    public String exportTrip(Trip trip) {
        return buildMarkdown(trip, false);
    }

    /** Renders the same content as {@link #exportTrip(Trip)} as a standalone HTML document. */
    public String exportTripAsHtml(Trip trip) {
        return exportTripAsHtml(trip, ImpressionsMode.NONE);
    }

    /**
     * Renders the trip as a standalone HTML document, optionally embedding a per-day image grid
     * discovered via the configured pattern selected by {@code mode} — see {@link ImpressionsMode}.
     * A pattern that isn't configured (or resolves no files for a given day) simply yields no
     * grid for that day; this is not treated as an error.
     */
    public String exportTripAsHtml(Trip trip, ImpressionsMode mode) {
        boolean includeMarkers = mode != ImpressionsMode.NONE;
        String markdown = buildMarkdown(trip, includeMarkers);
        Node document = Parser.builder().build().parse(markdown);
        String bodyHtml = HtmlRenderer.builder().build().render(document);
        if (includeMarkers) {
            bodyHtml = injectImpressions(bodyHtml, mode);
        }
        String title = trip.name() == null ? "" : escapeHtml(trip.name());
        return substitute(load(HTML_SHELL), Map.of("title", title, "body", bodyHtml));
    }

    private String buildMarkdown(Trip trip, boolean includeImpressionMarkers) {
        Objects.requireNonNull(trip, "trip");
        List<LocalDate> dates = store.listEntryDates(trip.slug());
        List<DiaryEntry> entries = dates.stream()
                .map(d -> store.loadEntry(trip.slug(), d))
                .toList();

        double totalDistance = entries.stream()
                .filter(e -> e.distance() != null)
                .mapToDouble(DiaryEntry::distance)
                .sum();
        double totalAltitude = entries.stream()
                .filter(e -> e.altitudeMeters() != null)
                .mapToDouble(DiaryEntry::altitudeMeters)
                .sum();
        LocalDate startDate = trip.startDate();
        LocalDate endDate = dates.isEmpty() ? null : dates.get(dates.size() - 1);

        StringBuilder entriesBlock = new StringBuilder();
        for (DiaryEntry e : entries) {
            if (entriesBlock.length() > 0) entriesBlock.append("\n\n");
            entriesBlock.append(renderEntry(trip, e, includeImpressionMarkers));
        }

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("tripName", trip.name() == null ? "" : trip.name());
        vars.put("tripDescription", trip.description() == null ? "" : trip.description().strip());
        vars.put("startDate", startDate == null ? MISSING : startDate.format(ISO));
        vars.put("endDate", endDate == null ? MISSING : endDate.format(ISO));
        vars.put("totalDays", totalDaysLabel(startDate, endDate));
        vars.put("entryCount", Integer.toString(dates.size()));
        vars.put("totalDistance", formatDistance(totalDistance));
        vars.put("totalAltitude", formatAltitude(totalAltitude));
        vars.put("entries", entriesBlock.toString());

        String out = substitute(load(SHELL), vars);
        return collapseBlankLines(out).strip() + "\n";
    }

    /** Replaces embedded {@code <!--IMPRESSIONS:yyyy-MM-dd-->} markers with an image grid table. */
    private String injectImpressions(String html, ImpressionsMode mode) {
        String pattern = switch (mode) {
            case FAVES -> store.getImpressionsFaveFilePattern().orElse(null);
            case ALL -> store.getImpressionsFilePattern().orElse(null);
            case NONE -> null;
        };
        int columns = Math.max(1, store.getImpressionsGridColumns());
        Matcher m = IMPRESSIONS_MARKER.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            LocalDate date = LocalDate.parse(m.group(1));
            String replacement = pattern == null ? "" : renderImpressionsTable(pattern, date, columns);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String renderImpressionsTable(String pattern, LocalDate date, int columns) {
        List<Path> images = impressionsResolver.resolve(pattern, date);
        if (images.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"impressions\">\n");
        for (int i = 0; i < images.size(); i++) {
            if (i % columns == 0) {
                if (i > 0) sb.append("</tr>\n");
                sb.append("<tr>\n");
            }
            String uri = images.get(i).toUri().toString();
            sb.append("<td><img src=\"").append(escapeHtml(uri)).append("\" /></td>\n");
        }
        sb.append("</tr>\n</table>\n");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String renderEntry(Trip trip, DiaryEntry e, boolean includeImpressionMarker) {
        StringBuilder sb = new StringBuilder();

        Map<String, String> h = new LinkedHashMap<>();
        h.put("date", e.date().format(ISO));
        h.put("weekday", e.date().format(WEEKDAY));
        h.put("daySegment", daySegment(trip.startDate(), e.date()));
        h.put("routeSegment", routeSegment(e.route()));
        sb.append(substitute(load(ENTRY_HEADING), h).stripTrailing());

        StringBuilder stats = new StringBuilder();
        if (e.distance() != null) {
            stats.append(substitute(load(ENTRY_DISTANCE),
                    Map.of("distance", formatDistance(e.distance()))).stripTrailing());
        }
        if (e.altitudeMeters() != null) {
            if (stats.length() > 0) stats.append("\n");
            stats.append(substitute(load(ENTRY_ALTITUDE),
                    Map.of("altitude", formatAltitude(e.altitudeMeters()))).stripTrailing());
        }
        if (e.trackUrl() != null && !e.trackUrl().isBlank()) {
            if (stats.length() > 0) stats.append("\n");
            stats.append(substitute(load(ENTRY_TRACK),
                    Map.of("trackUrl", e.trackUrl())).stripTrailing());
        }
        if (stats.length() > 0) {
            sb.append("\n\n").append(stats);
        }

        String tales = e.tales() == null ? "" : e.tales().strip();
        if (!tales.isBlank()) {
            String rendered = substitute(load(ENTRY_TALES), Map.of("tales", tales)).stripTrailing();
            sb.append("\n\n").append(rendered);
        }

        if (includeImpressionMarker) {
            sb.append("\n\n<!--IMPRESSIONS:").append(e.date().format(ISO)).append("-->");
        }

        return sb.toString();
    }

    private static String daySegment(LocalDate startDate, LocalDate entryDate) {
        if (startDate == null) return "";
        long day = ChronoUnit.DAYS.between(startDate, entryDate) + 1;
        return " Day " + day;
    }

    private static String routeSegment(String route) {
        if (route == null || route.isBlank() || DiaryEntry.DEFAULT_ROUTE.equals(route)) return "";
        return ": " + route;
    }

    private static String totalDaysLabel(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) return MISSING;
        return Long.toString(ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    private static String formatDistance(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String formatAltitude(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String substitute(String template, Map<String, String> vars) {
        String out = template;
        for (Map.Entry<String, String> v : vars.entrySet()) {
            out = out.replace("{{" + v.getKey() + "}}", v.getValue());
        }
        return out;
    }

    private static String collapseBlankLines(String s) {
        return s.replaceAll("\n{3,}", "\n\n");
    }

    private static String load(String resource) {
        try (InputStream in = DiaryExporter.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing template: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
