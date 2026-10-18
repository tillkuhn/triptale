package net.timafe.triptale.export;

import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.util.Markdown;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Image;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Renders a range of a trip's entries as a flattened plain-text summary (todo 67), meant to be
 * copied straight into a social media post. Only the tale's narrative paragraphs survive: no
 * headings, no entry facts (date, activity, distance, weather, coordinates), no links, no
 * markdown syntax, no images. Paragraph breaks within a day are kept (one blank line) so the
 * day's own structure stays readable; entries are separated by a wider gap (two blank lines) so
 * the day boundary itself stays visible too. A "To be continued ..." (trip language) teaser
 * closes the text, regardless of whether the selected range actually reaches the trip's last
 * entry — a reader scrolling a social media post has no use for that distinction anyway.
 */
@Component
public class SocialMediaExporter {

    private static final String HTML_SHELL = "/export/html-shell.html";
    private static final Parser PARSER = Parser.builder().build();

    private final MarkdownStore store;
    private final SettingsStore settingsStore;

    public SocialMediaExporter(MarkdownStore store, SettingsStore settingsStore) {
        this.store = store;
        this.settingsStore = settingsStore;
    }

    /**
     * Plain-text paragraphs for the entries dated {@code from}..{@code to} (inclusive; null =
     * unbounded), one day after another, closed with a "To be continued ..." teaser. Days with no
     * tale (or only a private one) are skipped entirely rather than leaving a stray gap; with no
     * text at all there is nothing to continue either, so the result is empty.
     */
    public String export(Trip trip, LocalDate from, LocalDate to) {
        Objects.requireNonNull(trip, "trip");
        List<LocalDate> dates = store.listEntryDates(trip.ref()).stream()
                .filter(d -> (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to)))
                .toList();
        List<String> days = new ArrayList<>();
        for (LocalDate date : dates) {
            String text = plainText(store.loadEntry(trip.ref(), date));
            if (!text.isBlank()) days.add(text);
        }
        if (days.isEmpty()) return "";
        String teaser = DiaryExporter.texts(settingsStore).word("toBeContinued");
        return String.join("\n\n\n", days).strip() + "\n\n\n" + teaser + "\n";
    }

    /**
     * A standalone HTML page that roughly shows how {@code plainText} will look once pasted
     * somewhere: each blank-line-separated block becomes its own paragraph, nothing else.
     */
    public String previewHtml(String title, String plainText) {
        String body = Arrays.stream(plainText.strip().split("\n{2,}"))
                .filter(p -> !p.isBlank())
                .map(p -> "<p>" + DiaryExporter.escapeHtml(p).replace("\n", "<br>") + "</p>")
                .collect(Collectors.joining("\n"));
        String lang = DiaryExporter.texts(settingsStore).language().code();
        return load(HTML_SHELL)
                .replace("{{lang}}", lang)
                .replace("{{title}}", title == null ? "" : DiaryExporter.escapeHtml(title))
                .replace("{{body}}", body);
    }

    /** The tale's paragraphs as plain text, blank if there is no (non-private) tale left. */
    private static String plainText(DiaryEntry e) {
        if (e.tales() == null || e.tales().isBlank()) return "";
        // Private sections (todo 48) never leave the app
        String tales = Markdown.stripPrivate(e.tales()).strip();
        if (tales.isEmpty()) return "";
        List<String> paragraphs = new ArrayList<>();
        collectParagraphs(PARSER.parse(tales), paragraphs);
        return String.join("\n\n", paragraphs);
    }

    /**
     * Walks top-level blocks: paragraphs and list items become their own plain-text block,
     * blockquotes are flattened into their contained paragraphs. Headings, code blocks, thematic
     * breaks and raw HTML carry no narrative text and are dropped entirely.
     */
    private static void collectParagraphs(Node parent, List<String> out) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            switch (n) {
                case Paragraph p -> addIfNotBlank(out, plainInline(p));
                case BlockQuote q -> collectParagraphs(q, out);
                case ListBlock l -> {
                    for (Node item = l.getFirstChild(); item != null; item = item.getNext()) {
                        collectParagraphs(item, out);
                    }
                }
                default -> { /* heading, code block, thematic break, raw HTML: no plain narrative text */ }
            }
        }
    }

    private static void addIfNotBlank(List<String> out, String text) {
        if (!text.isBlank()) out.add(text);
    }

    /**
     * Flattens a block's inline content to plain text: emphasis/strong/code markers are dropped,
     * links keep only their visible text (not the URL), images are skipped, line breaks become a
     * single space. Internal whitespace runs are collapsed so a soft-wrapped tale still reads as
     * one flowing block.
     */
    private static String plainInline(Node node) {
        StringBuilder sb = new StringBuilder();
        new PlainTextVisitor(sb).collect(node);
        return sb.toString().replaceAll("\\s+", " ").strip();
    }

    private static final class PlainTextVisitor extends AbstractVisitor {
        private final StringBuilder sb;

        PlainTextVisitor(StringBuilder sb) {
            this.sb = sb;
        }

        /** Entry point: {@link #visitChildren} is protected, this is the public way in. */
        void collect(Node node) {
            visitChildren(node);
        }

        @Override
        public void visit(Text text) {
            sb.append(text.getLiteral());
        }

        @Override
        public void visit(Code code) {
            sb.append(code.getLiteral());
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            sb.append(' ');
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            sb.append(' ');
        }

        @Override
        public void visit(Image image) {
            // No image is ever shown in plain text, and its alt text is not narrative prose
        }
    }

    private static String load(String resource) {
        try (InputStream in = SocialMediaExporter.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing template: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
