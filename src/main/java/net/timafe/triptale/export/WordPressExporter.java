package net.timafe.triptale.export;

import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.impressions.ImageFilter;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.util.Markdown;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Renders a range of a trip's entries as WordPress block markup (todo 44, stage 1), for pasting
 * into the block editor's Code editor. Per day: heading, stats, the tale as core blocks, and a
 * gallery of the day's backpack images, hotlinked via {@code attachments.publicBaseUrl}.
 */
@Component
public class WordPressExporter {

    private static final String PREVIEW_SHELL = "/export/wordpress-preview.html";
    private static final Parser PARSER = Parser.builder().build();
    // A soft line break is a space, as in the HTML export, not a <br>
    private static final HtmlRenderer HTML = HtmlRenderer.builder().softbreak(" ").build();
    // 20260530_091312_SaveAussicht+_mini.jpg: camera timestamp prefix, fave marker, size suffix
    private static final Pattern TIMESTAMP_PREFIX = Pattern.compile("^\\d{8}_\\d{6}_");
    private static final Pattern NAME_SUFFIX = Pattern.compile("(_mini|\\+)+$");
    private static final Pattern WORD_BOUNDARY = Pattern.compile(
            "(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})");

    private final MarkdownStore store;
    private final ImpressionsService impressions;
    private final AttachmentsDir attachmentsDir;
    private final SettingsStore settingsStore;

    public WordPressExporter(MarkdownStore store, ImpressionsService impressions, AttachmentsDir attachmentsDir,
                             SettingsStore settingsStore) {
        this.store = store;
        this.impressions = impressions;
        this.attachmentsDir = attachmentsDir;
        this.settingsStore = settingsStore;
    }

    /** Whether galleries are possible at all, i.e. a public base URL for the backpack is set. */
    public boolean imagesAvailable() {
        return !publicBaseUrl().isEmpty();
    }

    /**
     * Block markup for the entries dated {@code from}..{@code to} (inclusive; null = unbounded).
     * With {@code images}, each day gets a gallery of its backpack images, reduced to faves if
     * {@code favesOnly}; without a public base URL there are no galleries either way.
     */
    public String export(Trip trip, LocalDate from, LocalDate to, boolean images, boolean favesOnly) {
        Objects.requireNonNull(trip, "trip");
        String baseUrl = images ? publicBaseUrl() : "";
        ImageFilter faves = favesOnly ? impressions.faveFilter() : ImageFilter.parse("");
        int columns = Math.max(1, settingsStore.load().getImpressionsGridColumns());
        List<String> blocks = new ArrayList<>();
        for (LocalDate date : store.listEntryDates(trip.ref())) {
            if ((from != null && date.isBefore(from)) || (to != null && date.isAfter(to))) continue;
            DiaryEntry e = store.loadEntry(trip.ref(), date);
            String heading = DiaryExporter.headingLine(trip, e).replaceFirst("^#+\\s*", "");
            blocks.add(heading(2, DiaryExporter.escapeHtml(heading)));
            stats(e).ifPresent(blocks::add);
            if (e.tales() != null && !e.tales().isBlank()) {
                // Stored tale headings start at h2 (h1 is the entry title); under the h2 day heading they start at h3
                blocks.addAll(blocks(PARSER.parse(Markdown.shiftHeadings(e.tales().strip(), 1))));
            }
            if (!baseUrl.isEmpty()) {
                List<Path> dayImages = impressions.images(ImpressionSource.TRIP_ATTACHMENTS, trip, date);
                gallery(faves.apply(dayImages), baseUrl, columns).ifPresent(blocks::add);
            }
        }
        return blocks.isEmpty() ? "" : String.join("\n\n", blocks) + "\n";
    }

    /** A standalone HTML page that roughly shows how {@code markup} will look on the blog. */
    public String previewHtml(String title, String markup) {
        return load(PREVIEW_SHELL)
                .replace("{{title}}", DiaryExporter.escapeHtml(title == null ? "" : title))
                .replace("{{body}}", markup);
    }

    /** Distance, climb and a track link; parts that are unset or zero (e.g. train days) are left out. */
    private static Optional<String> stats(DiaryEntry e) {
        List<String> parts = new ArrayList<>();
        if (e.distance() != null && e.distance() > 0) {
            parts.add(DiaryExporter.formatDistance(e.distance()) + " km");
        }
        if (e.altitudeMeters() != null && e.altitudeMeters() > 0) {
            parts.add(DiaryExporter.formatAltitude(e.altitudeMeters()) + " m ↑");
        }
        if (e.trackUrl() != null && !e.trackUrl().isBlank()) {
            parts.add("<a href=\"" + DiaryExporter.escapeHtml(e.trackUrl().strip()) + "\">Track</a>");
        }
        return parts.isEmpty() ? Optional.empty()
                : Optional.of(block("paragraph", "", "<p>" + String.join(" · ", parts) + "</p>"));
    }

    private static List<String> blocks(Node parent) {
        List<String> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            out.add(block(n));
        }
        return out;
    }

    private static String block(Node n) {
        return switch (n) {
            case Paragraph p -> block("paragraph", "", render(p));
            case Heading h -> heading(h.getLevel(), render(h).replaceAll("^<h\\d>|</h\\d>$", ""));
            case ListBlock l when !containsList(l) -> list(l);
            case BlockQuote q -> block("quote", "",
                    "<blockquote class=\"wp-block-quote\">" + String.join("\n\n", blocks(q)) + "</blockquote>");
            case FencedCodeBlock c -> code(c.getLiteral());
            case IndentedCodeBlock c -> code(c.getLiteral());
            case ThematicBreak _ -> block("separator", "", "<hr class=\"wp-block-separator has-alpha-channel-opacity\"/>");
            // Nested lists, raw HTML and anything else stay editable as a Custom HTML block
            default -> block("html", "", render(n));
        };
    }

    private static String heading(int level, String innerHtml) {
        String attrs = level == 2 ? "" : "{\"level\":" + level + "}";
        return block("heading", attrs, "<h" + level + " class=\"wp-block-heading\">" + innerHtml + "</h" + level + ">");
    }

    /** A flat list; since WordPress 6.1 every item is its own list-item block. */
    private static String list(ListBlock l) {
        String html = render(l)
                .replaceFirst("^<(ul|ol)", "<$1 class=\"wp-block-list\"")
                .replace("<li>", "<!-- wp:list-item -->\n<li>")
                .replace("</li>", "</li>\n<!-- /wp:list-item -->");
        String attrs = "";
        if (l instanceof OrderedList ol) {
            Integer start = ol.getMarkerStartNumber();
            attrs = start == null || start == 1 ? "{\"ordered\":true}" : "{\"ordered\":true,\"start\":" + start + "}";
        }
        return block("list", attrs, html);
    }

    private static boolean containsList(Node node) {
        for (Node n = node.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof ListBlock || containsList(n)) return true;
        }
        return false;
    }

    private static String code(String literal) {
        return block("code", "",
                "<pre class=\"wp-block-code\"><code>" + DiaryExporter.escapeHtml(literal.stripTrailing()) + "</code></pre>");
    }

    private Optional<String> gallery(List<Path> images, String baseUrl, int columns) {
        Path root = attachmentsDir.root();
        List<String> items = images.stream()
                .filter(p -> p.startsWith(root))
                .map(p -> block("image", "{\"sizeSlug\":\"large\",\"linkDestination\":\"none\"}",
                        "<figure class=\"wp-block-image size-large\"><img src=\""
                                + DiaryExporter.escapeHtml(baseUrl + urlPath(root.relativize(p)))
                                + "\" alt=\"" + DiaryExporter.escapeHtml(altText(p)) + "\"/></figure>"))
                .toList();
        if (items.isEmpty()) return Optional.empty();
        return Optional.of(block("gallery", "{\"columns\":" + columns + ",\"linkTo\":\"none\"}",
                "<figure class=\"wp-block-gallery has-nested-images columns-" + columns + " is-cropped\">"
                        + String.join("\n", items) + "</figure>"));
    }

    /**
     * Percent-encodes each segment: S3 reads a literal {@code +} in a URL path as a space, so
     * fave names like {@code Kiwieck+_mini.jpg} would answer 403 unencoded.
     */
    static String urlPath(Path relative) {
        return StreamSupport.stream(relative.spliterator(), false)
                .map(seg -> URLEncoder.encode(seg.toString(), StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    /** {@code 20260529_142327_OoopsICE1011FaelltAus+_mini.jpg} → "Ooops ICE 1011 Faellt Aus". */
    static String altText(Path image) {
        String name = image.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        name = NAME_SUFFIX.matcher(TIMESTAMP_PREFIX.matcher(name).replaceFirst("")).replaceFirst("");
        name = WORD_BOUNDARY.matcher(name.replace('_', ' ').replace('-', ' ')).replaceAll(" ");
        return name.replaceAll("\\s+", " ").strip();
    }

    private String publicBaseUrl() {
        String url = settingsStore.load().getAttachments().getPublicBaseUrl().strip();
        return url.isEmpty() || url.endsWith("/") ? url : url + "/";
    }

    private static String render(Node n) {
        return HTML.render(n).strip();
    }

    private static String block(String name, String attrs, String html) {
        String open = attrs.isEmpty() ? name : name + " " + attrs;
        return "<!-- wp:" + open + " -->\n" + html + "\n<!-- /wp:" + name + " -->";
    }

    private static String load(String resource) {
        try (InputStream in = WordPressExporter.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing template: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
