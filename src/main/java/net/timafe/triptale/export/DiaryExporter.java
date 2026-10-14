package net.timafe.triptale.export;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.LinkKind;
import net.timafe.triptale.domain.Post;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.i18n.ExportTexts;
import net.timafe.triptale.i18n.TripLanguage;
import net.timafe.triptale.impressions.ImageFilter;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.util.Markdown;
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
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiaryExporter {

    private static final String SHELL = "/export/diary-template.md";
    private static final String ENTRY_HEADING = "/export/entry-heading.md";
    private static final String ENTRY_FACTS = "/export/entry-facts.md";
    private static final String ENTRY_LINKS = "/export/entry-links.md";
    private static final String ENTRY_TALES = "/export/entry-tales.md";
    private static final String HTML_SHELL = "/export/html-shell.html";

    static final String OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String MISSING = "—";
    // {{t.stats.distance}} in a template: a word from ExportTexts
    private static final Pattern TEXT_VAR = Pattern.compile("\\{\\{t\\.([\\w.]+)}}");
    private static final Pattern IMPRESSIONS_MARKER = Pattern.compile("<!--IMPRESSIONS:(\\d{4}-\\d{2}-\\d{2})-->");

    private final MarkdownStore store;
    private final ImpressionsService impressions;
    private final SettingsStore settingsStore;

    public DiaryExporter(MarkdownStore store, ImpressionsService impressions, SettingsStore settingsStore) {
        this.store = store;
        this.impressions = impressions;
        this.settingsStore = settingsStore;
    }

    public String exportTrip(Trip trip) {
        return exportTrip(trip, null, null);
    }

    /**
     * Exports the entries dated {@code from}..{@code to} (inclusive; null = unbounded). The header
     * totals cover that range only; "Day N" stays relative to the trip start.
     */
    public String exportTrip(Trip trip, LocalDate from, LocalDate to) {
        Objects.requireNonNull(trip, "trip");
        List<LocalDate> allDates = store.listEntryDates(trip.ref());
        List<LocalDate> dates = allDates.stream()
                .filter(d -> (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to)))
                .toList();
        // A range starting at the trip's first entry keeps the trip's own start date.
        LocalDate startDate = dates.isEmpty() || dates.getFirst().equals(allDates.getFirst())
                ? trip.startDate() : dates.getFirst();
        LocalDate endDate = dates.isEmpty() ? null : dates.getLast();
        return buildMarkdown(trip, dates, trip.name(), trip.description(), startDate, endDate, false, false);
    }

    /**
     * Exports a Post (todo 60): its own {@code title}/{@code intro} replace the trip name/description
     * in the header, and {@code entryDates} (already resolved and sorted by {@code MarkdownStore})
     * stand in for a contiguous range — a Post's days may have gaps. "Day N" still stays relative to
     * the trip start, same as a range export.
     */
    public String exportPost(Trip trip, Post post) {
        Objects.requireNonNull(trip, "trip");
        Objects.requireNonNull(post, "post");
        List<LocalDate> dates = post.entryDates();
        LocalDate startDate = dates.isEmpty() ? null : dates.getFirst();
        LocalDate endDate = dates.isEmpty() ? null : dates.getLast();
        return buildMarkdown(trip, dates, post.title(), post.intro(), startDate, endDate, false, false);
    }

    /** Renders the same content as {@link #exportTrip(Trip)} as a standalone HTML document. */
    public String exportTripAsHtml(Trip trip) {
        return exportTripAsHtml(trip, null, null, null, false);
    }

    /**
     * Renders the entries dated {@code from}..{@code to} as a standalone HTML document,
     * optionally embedding a per-day image grid from {@code images} (null = no images), reduced
     * to faves if {@code favesOnly}. A source that yields no files for a given day simply gives
     * no grid for that day; this is not treated as an error.
     */
    public String exportTripAsHtml(Trip trip, LocalDate from, LocalDate to,
                                   ImpressionSource images, boolean favesOnly) {
        Objects.requireNonNull(trip, "trip");
        List<LocalDate> allDates = store.listEntryDates(trip.ref());
        List<LocalDate> dates = allDates.stream()
                .filter(d -> (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to)))
                .toList();
        LocalDate startDate = dates.isEmpty() || dates.getFirst().equals(allDates.getFirst())
                ? trip.startDate() : dates.getFirst();
        LocalDate endDate = dates.isEmpty() ? null : dates.getLast();
        return renderHtml(trip, dates, trip.name(), trip.description(), startDate, endDate, images, favesOnly);
    }

    /** Renders a Post (see {@link #exportPost(Trip, Post)}) as a standalone HTML document. */
    public String exportPostAsHtml(Trip trip, Post post, ImpressionSource images, boolean favesOnly) {
        Objects.requireNonNull(trip, "trip");
        Objects.requireNonNull(post, "post");
        List<LocalDate> dates = post.entryDates();
        LocalDate startDate = dates.isEmpty() ? null : dates.getFirst();
        LocalDate endDate = dates.isEmpty() ? null : dates.getLast();
        return renderHtml(trip, dates, post.title(), post.intro(), startDate, endDate, images, favesOnly);
    }

    private String renderHtml(Trip trip, List<LocalDate> dates, String headingTitle, String headingDescription,
                              LocalDate startDate, LocalDate endDate, ImpressionSource images, boolean favesOnly) {
        boolean includeMarkers = images != null;
        String markdown = buildMarkdown(trip, dates, headingTitle, headingDescription, startDate, endDate, includeMarkers, true);
        Node document = Parser.builder().build().parse(markdown);
        String bodyHtml = HtmlRenderer.builder().build().render(document);
        if (includeMarkers) {
            bodyHtml = injectImpressions(bodyHtml, trip, images, favesOnly);
        }
        String title = headingTitle == null ? "" : escapeHtml(headingTitle);
        return substitute(load(HTML_SHELL), Map.of("lang", texts().language().code(), "title", title, "body", bodyHtml));
    }

    /** Texts in the trip language, currently always the app-wide default (todo 30). */
    ExportTexts texts() {
        return texts(settingsStore);
    }

    static ExportTexts texts(SettingsStore settingsStore) {
        return ExportTexts.of(TripLanguage.fromCode(settingsStore.load().getDefaultTripLanguage()));
    }

    /** With {@code htmlFacts}, each entry gets the facts strip as an HTML block instead of the Markdown byline. */
    private String buildMarkdown(Trip trip, List<LocalDate> dates, String headingTitle, String headingDescription,
                                 LocalDate startDate, LocalDate endDate,
                                 boolean includeImpressionMarkers, boolean htmlFacts) {
        ExportTexts texts = texts();
        List<DiaryEntry> entries = dates.stream()
                .map(d -> store.loadEntry(trip.ref(), d))
                .toList();

        double totalDistance = entries.stream()
                .filter(e -> e.distance() != null)
                .mapToDouble(DiaryEntry::distance)
                .sum();
        double totalAltitude = entries.stream()
                .filter(e -> e.altitudeMeters() != null)
                .mapToDouble(DiaryEntry::altitudeMeters)
                .sum();

        StringBuilder entriesBlock = new StringBuilder();
        for (DiaryEntry e : entries) {
            if (entriesBlock.length() > 0) entriesBlock.append("\n\n");
            entriesBlock.append(renderEntry(trip, e, texts, includeImpressionMarkers, htmlFacts));
        }

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("tripName", headingTitle == null ? "" : headingTitle);
        vars.put("tripDescription", headingDescription == null ? "" : headingDescription.strip());
        vars.put("startDate", startDate == null ? MISSING : texts.longDate(startDate));
        vars.put("endDate", endDate == null ? MISSING : texts.longDate(endDate));
        long totalDays = totalDays(startDate, endDate);
        vars.put("totalDays", totalDays < 0 ? MISSING : Long.toString(totalDays));
        vars.put("daysLabel", texts.plural("days", totalDays));
        vars.put("entryCount", Integer.toString(dates.size()));
        vars.put("entriesLabel", texts.plural("entries", dates.size()));
        vars.put("totalDistance", texts.distance(totalDistance));
        vars.put("totalAltitude", texts.altitude(totalAltitude));
        // Place names from the Coordinator's lookup are OpenStreetMap data (ODbL): credit once at the end
        String attribution = "";
        if (entries.stream().anyMatch(EntryFacts::hasPlace)) {
            attribution = htmlFacts
                    ? "<p class=\"credits\">" + placesCredit("<a href=\"" + OSM_COPYRIGHT_URL + "\">OpenStreetMap</a>") + "</p>"
                    : "*" + placesCredit("[OpenStreetMap](" + OSM_COPYRIGHT_URL + ")") + "*";
        }
        vars.put("attribution", attribution);

        // Words go in before the entries, so a "{{t.…}}" typed in a tale stays as written
        String out = substitute(translate(load(SHELL), texts), vars);
        out = out.replace("{{entries}}", entriesBlock.toString());
        return collapseBlankLines(out).strip() + "\n";
    }

    /** Replaces embedded {@code <!--IMPRESSIONS:yyyy-MM-dd-->} markers with an image grid table. */
    private String injectImpressions(String html, Trip trip, ImpressionSource source, boolean favesOnly) {
        AppSettings settings = settingsStore.load();
        ImageFilter faves = favesOnly ? impressions.faveFilter() : ImageFilter.parse("");
        int columns = Math.max(1, settings.getImpressionsGridColumns());
        Matcher m = IMPRESSIONS_MARKER.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            LocalDate date = LocalDate.parse(m.group(1));
            String replacement = renderImpressionsTable(faves.apply(impressions.images(source, trip, date)), columns);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String renderImpressionsTable(List<Path> images, int columns) {
        if (images.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"impressions\" style=\"column-count: ").append(columns).append(";\">\n");
        for (Path image : images) {
            String uri = image.toUri().toString();
            sb.append("<img src=\"").append(escapeHtml(uri)).append("\" />\n");
        }
        sb.append("</div>\n");
        return sb.toString();
    }

    /** The OpenStreetMap credit, always in English (OSM's own wording); {@code osmLink} is the exporter's link markup. */
    static String placesCredit(String osmLink) {
        return "Place names © " + osmLink + " contributors";
    }

    static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * The entry heading line, e.g. {@code ## A → B}. Without a real title it falls back to the day
     * ({@code ## Day 3}), or to the date if the trip has no start date; the facts carry the date.
     */
    static String headingLine(Trip trip, DiaryEntry e, ExportTexts texts) {
        return substitute(load(ENTRY_HEADING), Map.of("title", headingTitle(trip, e, texts))).strip();
    }

    private static String headingTitle(Trip trip, DiaryEntry e, ExportTexts texts) {
        String title = e.title() == null ? "" : e.title().strip();
        if (!title.isEmpty() && !title.equals(DiaryEntry.DEFAULT_TITLE)) return title;
        Long day = EntryFacts.dayNumber(trip.startDate(), e.date());
        if (day != null) return texts.word("day") + " " + day;
        return texts.weekday(e.date()) + ", " + texts.longDate(e.date());
    }

    private String renderEntry(Trip trip, DiaryEntry e, ExportTexts texts, boolean includeImpressionMarker,
                               boolean htmlFacts) {
        StringBuilder sb = new StringBuilder();
        sb.append(headingLine(trip, e, texts));

        // HTML exports get the ticket stub strip as a raw HTML block, plain Markdown a one-line byline
        EntryFacts facts = EntryFacts.of(trip.startDate(), e, texts);
        sb.append("\n\n").append(htmlFacts ? facts.html()
                : substitute(load(ENTRY_FACTS), Map.of("facts", facts.byline())).stripTrailing());

        // Private sections (todo 48) never leave the app
        String tales = e.tales() == null ? "" : Markdown.shiftHeadings(Markdown.stripPrivate(e.tales()).strip(), 1);
        if (!tales.isBlank()) {
            String rendered = substitute(load(ENTRY_TALES), Map.of("tales", tales)).stripTrailing();
            sb.append("\n\n").append(rendered);
        }

        if (!e.links().isEmpty()) {
            StringBuilder links = new StringBuilder();
            e.links().forEach((verb, link) -> links.append(substitute(load(ENTRY_LINKS), Map.of(
                    "emoji", LinkKind.emojiFor(verb),
                    "label", texts.linkLabel(verb),
                    // Invalid values (hand-edited, not a URL) go out as plain text rather than a broken link
                    "link", link.valid() ? "[" + link.displayText() + "](" + link.url() + ")" : link.url()))));
            sb.append("\n\n").append(links.toString().stripTrailing());
        }

        if (includeImpressionMarker) {
            sb.append("\n\n<!--IMPRESSIONS:").append(e.date().format(ISO)).append("-->");
        }

        return sb.toString();
    }

    /** Days from start to end, both inclusive; -1 if either is unknown. */
    private static long totalDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) return -1;
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** Replaces {@code {{t.key}}} placeholders with words in the trip language. */
    private static String translate(String template, ExportTexts texts) {
        return TEXT_VAR.matcher(template).replaceAll(m -> Matcher.quoteReplacement(texts.word(m.group(1))));
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
