package net.timafe.triptale.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Curated travel-related emoji for the emoji picker (todo 69), read from the bundled
 * {@code emojis.txt} (one {@code emoji | comma, separated, keywords} per line; blank lines and
 * {@code #} comments are skipped). Deliberately a small, hand-picked list — not the full Unicode
 * emoji catalog, see {@code docs/69_emoji_picker.md}.
 */
public final class Emojis {

    static final String RESOURCE = "/emojis.txt";

    /** One emoji entry; {@code keywords} are lowercase, used for search matching only. */
    public record Entry(String glyph, List<String> keywords) {
    }

    private final List<Entry> entries;

    Emojis(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    /** Loads the bundled list; a missing resource yields an empty list rather than an error. */
    public static Emojis load() {
        try (InputStream in = Emojis.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return new Emojis(List.of());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return new Emojis(parse(reader.lines().toList()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Lines without a {@code |} separator are skipped. */
    static List<Entry> parse(List<String> lines) {
        return lines.stream()
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .filter(l -> l.contains("|"))
                .map(l -> {
                    String glyph = l.substring(0, l.indexOf('|')).strip();
                    String rest = l.substring(l.indexOf('|') + 1).strip();
                    List<String> keywords = Arrays.stream(rest.split(","))
                            .map(k -> k.strip().toLowerCase(Locale.ROOT))
                            .filter(k -> !k.isEmpty())
                            .toList();
                    return new Entry(glyph, keywords);
                })
                .toList();
    }

    public List<Entry> all() {
        return entries;
    }

    /**
     * Case-insensitive substring match against each entry's keywords. A blank query returns
     * every entry (in file order).
     */
    public List<Entry> search(String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return entries;
        return entries.stream()
                .filter(e -> e.keywords().stream().anyMatch(k -> k.contains(q)))
                .toList();
    }
}
