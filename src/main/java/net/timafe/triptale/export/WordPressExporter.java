package net.timafe.triptale.export;

import net.timafe.triptale.attachments.InlineImages;
import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Link;
import net.timafe.triptale.domain.LinkKind;
import net.timafe.triptale.domain.Post;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.i18n.ExportTexts;
import net.timafe.triptale.impressions.ImageFilter;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.util.Markdown;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Renders a range of a trip's entries as WordPress block markup (todo 44, stage 1), for pasting
 * into the block editor's Code editor. Per day: heading, facts, the tale as core blocks, and a
 * gallery of the day's backpack images, hotlinked via {@code attachments.publicBaseUrl}.
 */
@Component
public class WordPressExporter {

    /** Level of each day's title; headings inside the tale always sit below it. */
    private static final int DAY_HEADING_LEVEL = 3;

    private static final String PREVIEW_SHELL = "/export/wordpress-preview.html";
    private static final Parser PARSER = Parser.builder().build();
    // A soft line break is a space, as in the HTML export, not a <br>
    private static final HtmlRenderer HTML = HtmlRenderer.builder().softbreak(" ").build();

    private final MarkdownStore store;
    private final ImpressionsService impressions;
    private final AttachmentsDir attachmentsDir;
    private final SettingsStore settingsStore;
    private volatile List<String> lastInlineImageProblems = List.of();

    /** What the blocks of one export need to place inline images (todo 92), and where they report problems. */
    private record Ctx(Path dataDir, String baseUrl, List<String> problems) {
    }

    public WordPressExporter(MarkdownStore store, ImpressionsService impressions, AttachmentsDir attachmentsDir,
                             SettingsStore settingsStore) {
        this.store = store;
        this.impressions = impressions;
        this.attachmentsDir = attachmentsDir;
        this.settingsStore = settingsStore;
    }

    /** What was wrong with inline images (todo 92) in the last export: empty if nothing. */
    public List<String> lastInlineImageProblems() {
        return lastInlineImageProblems;
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
        List<LocalDate> dates = store.listEntryDates(trip.ref()).stream()
                .filter(d -> (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to)))
                .toList();
        return export(trip, dates, images, favesOnly);
    }

    private Ctx newCtx() {
        return new Ctx(store.dataDir(), publicBaseUrl(), new ArrayList<>());
    }

    /**
     * Block markup for exactly {@code dates} (already resolved and sorted, possibly with gaps —
     * see {@link #exportPost}), same rendering as the range-based
     * {@link #export(Trip, LocalDate, LocalDate, boolean, boolean)}.
     */
    public String export(Trip trip, List<LocalDate> dates, boolean images, boolean favesOnly) {
        Ctx ctx = newCtx();
        String markup = export(trip, dates, images, favesOnly, ctx);
        lastInlineImageProblems = List.copyOf(ctx.problems());
        return markup;
    }

    private String export(Trip trip, List<LocalDate> dates, boolean images, boolean favesOnly, Ctx ctx) {
        Objects.requireNonNull(trip, "trip");
        String baseUrl = images ? publicBaseUrl() : "";
        ImageFilter faves = favesOnly ? impressions.faveFilter() : ImageFilter.parse("");
        int columns = Math.max(1, settingsStore.load().getImpressionsGridColumns());
        ExportTexts texts = DiaryExporter.texts(settingsStore);
        List<String> blocks = new ArrayList<>();
        boolean places = false;
        for (LocalDate date : dates) {
            DiaryEntry e = store.loadEntry(trip.ref(), date);
            String heading = DiaryExporter.headingLine(trip, e, texts).replaceFirst("^#+\\s*", "");
            blocks.add(heading(DAY_HEADING_LEVEL, DiaryExporter.escapeHtml(heading)));
            blocks.add(facts(EntryFacts.of(trip.startDate(), e, texts)));
            places |= EntryFacts.hasPlace(e);
            // Private sections (todo 48) never leave the app
            String tales = e.tales() == null ? "" : Markdown.stripPrivate(e.tales()).strip();
            if (!tales.isEmpty()) {
                // Stored tale headings start at h2 (h1 is the entry title); under the h2 day heading they start at h3
                blocks.addAll(blocks(PARSER.parse(Markdown.shiftHeadingsToMin(tales, DAY_HEADING_LEVEL + 1)), ctx));
            }
            blocks.addAll(linkBlocks(e, texts));
            if (!baseUrl.isEmpty()) {
                List<Path> dayImages = impressions.images(ImpressionSource.TRIP_ATTACHMENTS, trip, date);
                // Images placed in the text (todo 92) are not repeated in the gallery
                Set<Path> inline = InlineImages.referenced(tales, ctx.dataDir());
                gallery(faves.apply(dayImages).stream().filter(p -> !inline.contains(p.normalize())).toList(),
                        baseUrl, columns).ifPresent(blocks::add);
            }
        }
        // Place names from the Coordinator's lookup are OpenStreetMap data (ODbL): credit once at the end
        if (places) {
            String credit = DiaryExporter.placesCredit("<a href=\"" + DiaryExporter.OSM_COPYRIGHT_URL + "\">OpenStreetMap</a>");
            blocks.add(block("paragraph", "{\"fontSize\":\"small\"}", "<p class=\"has-small-font-size\">" + credit + "</p>"));
        }
        return blocks.isEmpty() ? "" : String.join("\n\n", blocks) + "\n";
    }

    /**
     * Block markup for a Post (todo 60): its intro as leading paragraph block(s), followed by the
     * per-day blocks for the trip's entries within the post's range. No title heading: WordPress shows the post
     * title from its own field, a heading would repeat it.
     */
    public String exportPost(Trip trip, Post post, boolean images, boolean favesOnly) {
        Objects.requireNonNull(trip, "trip");
        Objects.requireNonNull(post, "post");
        String intro = post.intro() == null ? "" : Markdown.stripPrivate(post.intro()).strip();
        Ctx ctx = newCtx();
        List<LocalDate> dates = store.listEntryDates(trip.ref()).stream()
                .filter(d -> (post.from() == null || !d.isBefore(post.from())) && (post.to() == null || !d.isAfter(post.to())))
                .toList();
        String body = export(trip, dates, images, favesOnly, ctx);
        String result;
        if (intro.isEmpty()) {
            result = body;
        } else {
            String leading = String.join("\n\n", blocks(PARSER.parse(intro), ctx));
            result = body.isEmpty() ? leading + "\n" : leading + "\n\n" + body;
        }
        lastInlineImageProblems = List.copyOf(ctx.problems());
        return result;
    }

    /** A standalone HTML page that roughly shows how {@code markup} will look on the blog. */
    public String previewHtml(String title, String markup) {
        return load(PREVIEW_SHELL)
                .replace("{{lang}}", DiaryExporter.texts(settingsStore).language().code())
                .replace("{{title}}", DiaryExporter.escapeHtml(title == null ? "" : title))
                .replace("{{body}}", markup);
    }

    /**
     * The entry facts (todo 53) as one small paragraph of two lines: the day, then activity,
     * weather and location. The earlier columns layout (big emoji over bold text) was far too
     * prominent on the blog. Coordinates keep their lat/lon together with a no-break space. No
     * custom CSS, so the theme styles it.
     */
    private static String facts(EntryFacts facts) {
        List<String> lines = facts.twoLines();
        String html = DiaryExporter.escapeHtml(lines.get(0));
        if (!lines.get(1).isEmpty()) {
            html += "<br>" + DiaryExporter.escapeHtml(lines.get(1)).replaceAll("([NS]) (\\d)", "$1\u00A0$2");
        }
        return block("paragraph", "{\"fontSize\":\"small\"}", "<p class=\"has-small-font-size\">" + html + "</p>");
    }

    /**
     * The entry's links, same wording as the Markdown export; invalid values as plain text. A
     * {@code watch} link to a YouTube video is pulled out of the list and rendered as its own
     * {@code wp:embed} block instead (matching what the block editor's "Embed" produces when you
     * paste the URL directly), so the blog shows an inline player rather than a bare link.
     */
    private static List<String> linkBlocks(DiaryEntry e, ExportTexts texts) {
        List<String> out = new ArrayList<>();
        if (e.links().isEmpty()) return out;
        StringBuilder html = new StringBuilder("<ul class=\"wp-block-list\">");
        List<String> embeds = new ArrayList<>();
        boolean anyListItem = false;
        for (var entry : e.links().entrySet()) {
            String verb = entry.getKey();
            Link link = entry.getValue();
            if (LinkKind.WATCH.key().equals(verb) && link.valid() && isYouTubeUrl(link.url())) {
                embeds.add(youTubeEmbed(link.url()));
                continue;
            }
            anyListItem = true;
            String text = DiaryExporter.escapeHtml(link.valid() ? link.displayText() : link.url());
            html.append("<!-- wp:list-item -->\n<li>")
                    .append(LinkKind.emojiFor(verb)).append(' ')
                    .append(DiaryExporter.escapeHtml(texts.linkLabel(verb))).append(": ")
                    .append(link.valid() ? "<a href=\"" + DiaryExporter.escapeHtml(link.url()) + "\">" + text + "</a>" : text)
                    .append("</li>\n<!-- /wp:list-item -->");
        }
        if (anyListItem) out.add(block("list", "", html.append("</ul>").toString()));
        out.addAll(embeds);
        return out;
    }

    /** Hosts WordPress's own "Embed" block recognizes as YouTube: {@code youtube.com} (any subdomain) or {@code youtu.be}. */
    private static boolean isYouTubeUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** A {@code wp:embed} block for a YouTube URL, identical to what pasting the URL into the block editor produces. */
    private static String youTubeEmbed(String url) {
        String jsonUrl = jsonEscape(url);
        String attrs = "{\"url\":\"" + jsonUrl + "\",\"type\":\"video\",\"providerNameSlug\":\"youtube\","
                + "\"responsive\":true,\"className\":\"wp-embed-aspect-16-9 wp-has-aspect-ratio\"}";
        String html = "<figure class=\"wp-block-embed is-type-video is-provider-youtube wp-block-embed-youtube "
                + "wp-embed-aspect-16-9 wp-has-aspect-ratio\"><div class=\"wp-block-embed__wrapper\">\n"
                + DiaryExporter.escapeHtml(url) + "\n</div></figure>";
        return block("embed", attrs, html);
    }

    /** Minimal JSON string escaping for a URL inside a {@code wp:embed} block's attribute comment. */
    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<String> blocks(Node parent, Ctx ctx) {
        List<String> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            String block = block(n, ctx);
            if (!block.isEmpty()) out.add(block);
        }
        return out;
    }

    private static String block(Node n, Ctx ctx) {
        return switch (n) {
            case Paragraph p -> inlineImage(p, ctx).orElseGet(() -> block("paragraph", "", render(p)));
            case Heading h -> heading(h.getLevel(), render(h).replaceAll("^<h\\d>|</h\\d>$", ""));
            case ListBlock l when !containsList(l) -> list(l);
            case BlockQuote q -> block("quote", "",
                    "<blockquote class=\"wp-block-quote\">" + String.join("\n\n", blocks(q, ctx)) + "</blockquote>");
            case FencedCodeBlock c -> code(c.getLiteral());
            case IndentedCodeBlock c -> code(c.getLiteral());
            case ThematicBreak _ -> block("separator", "", "<hr class=\"wp-block-separator has-alpha-channel-opacity\"/>");
            // Nested lists, raw HTML and anything else stay editable as a Custom HTML block
            default -> block("html", "", render(n));
        };
    }

    /**
     * A paragraph that is just one {@code attachments/} image (todo 92) as a core image block, hotlinked like the
     * gallery. Empty result = not an inline image; a block without a usable file or public base URL gives an
     * empty string, so it is left out; both report a problem. A block from elsewhere, or an image inside a
     * sentence, stays as written.
     */
    private static Optional<String> inlineImage(Paragraph p, Ctx ctx) {
        for (Node n = p.getFirstChild(); n != null; n = n.getNext()) {
            if (!(n instanceof Image img) || !InlineImages.isAttachmentRef(img.getDestination())) continue;
            if (p.getFirstChild() != img || img.getNext() != null) {
                ctx.problems().add("not on its own line: " + img.getDestination());
                continue;
            }
            Optional<Path> file = InlineImages.resolve(ctx.dataDir(), img.getDestination());
            if (file.isEmpty()) {
                ctx.problems().add("file not found: " + img.getDestination());
                return Optional.of("");
            }
            if (ctx.baseUrl().isEmpty()) {
                ctx.problems().add("no public base URL in Settings: " + img.getDestination());
                return Optional.of("");
            }
            String alt = altOf(img);
            if (alt.isBlank()) alt = InlineImages.altText(file.get());
            String src = DiaryExporter.escapeHtml(ctx.baseUrl() + urlPath(ctx.dataDir().resolve(AttachmentsDir.DIR_NAME)
                    .relativize(file.get())));
            return Optional.of(block("image", "{\"lightbox\":{\"enabled\":true},\"sizeSlug\":\"large\",\"linkDestination\":\"none\"}",
                    "<figure class=\"wp-block-image size-large\"><img src=\"" + src + "\" alt=\""
                            + DiaryExporter.escapeHtml(alt) + "\"/><figcaption class=\"wp-element-caption\">"
                            + DiaryExporter.escapeHtml(alt) + "</figcaption></figure>"));
        }
        return Optional.empty();
    }

    private static String altOf(Node image) {
        StringBuilder sb = new StringBuilder();
        for (Node n = image.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof Text t) sb.append(t.getLiteral());
        }
        return sb.toString();
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
        List<String> items = impressions.galleryRows(images.stream().filter(p -> p.startsWith(root)).toList()).stream()
                .flatMap(row -> row.images().stream())
                .map(p -> block("image", "{\"lightbox\":{\"enabled\":true},\"sizeSlug\":\"large\",\"linkDestination\":\"none\"}",
                        "<figure class=\"wp-block-image size-large\"><img src=\""
                                + DiaryExporter.escapeHtml(baseUrl + urlPath(root.relativize(p)))
                                + "\" alt=\"" + DiaryExporter.escapeHtml(InlineImages.altText(p)) + "\"/></figure>"))
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
