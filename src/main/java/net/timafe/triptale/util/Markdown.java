package net.timafe.triptale.util;

import java.util.List;
import java.util.Locale;

public final class Markdown {

    private static final String H1_PREFIX = "# ";
    private static final String H2_PREFIX = "## ";

    private Markdown() {}

    /**
     * Rewrites any line whose content starts with exactly {@code "# "} (a single {@code #}
     * followed by a space) to start with {@code "## "} instead. Lines already starting with
     * {@code "## "}, {@code "### "}, etc. are left untouched, as is a bare {@code "#"} with no
     * trailing space. Naive whole-body line scan — no fenced-code-block awareness.
     */
    public static String demoteH1(String body) {
        if (body == null || body.isEmpty()) return body;
        String[] lines = body.split("\n", -1);
        StringBuilder sb = new StringBuilder(body.length() + 16);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith(H1_PREFIX)) {
                sb.append(H2_PREFIX).append(line.substring(H1_PREFIX.length()));
            } else {
                sb.append(line);
            }
            if (i < lines.length - 1) sb.append('\n');
        }
        return sb.toString();
    }

    private static final int MAX_HEADING_LEVEL = 6;

    /**
     * Shifts every ATX heading line (matching {@code ^#{1,6} }) in {@code body} by {@code n}
     * levels, clamped to a maximum of {@value #MAX_HEADING_LEVEL} {@code #} characters
     * (CommonMark's max heading level). Lines with 7 or more leading {@code #} characters before
     * the space are not valid ATX headings and are left untouched, consistent with
     * {@link #demoteH1}'s existing approach. Naive whole-body line scan — no fenced-code-block
     * awareness.
     */
    public static String shiftHeadings(String body, int n) {
        if (body == null || body.isEmpty()) return body;
        String[] lines = body.split("\n", -1);
        StringBuilder sb = new StringBuilder(body.length() + 16);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int level = headingLevel(line);
            if (level > 0) {
                int shifted = Math.min(MAX_HEADING_LEVEL, level + n);
                sb.append("#".repeat(shifted)).append(' ').append(line.substring(level + 1));
            } else {
                sb.append(line);
            }
            if (i < lines.length - 1) sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Returns the ATX heading level (1-6) of {@code line} if it matches {@code ^#{1,6} },
     * otherwise 0 (not a heading, including 7+ leading {@code #} before the space).
     */
    private static int headingLevel(String line) {
        int hashes = 0;
        while (hashes < line.length() && line.charAt(hashes) == '#') hashes++;
        if (hashes < 1 || hashes > MAX_HEADING_LEVEL) return 0;
        if (hashes >= line.length() || line.charAt(hashes) != ' ') return 0;
        return hashes;
    }

    /** Heading keywords that mark a private section (todo 48), German variants as a safety net. */
    private static final List<String> PRIVATE_KEYWORDS = List.of("internal", "private", "intern", "privat");

    /**
     * Removes private sections from a tale: a heading whose text starts with one of
     * {@link #PRIVATE_KEYWORDS} (case-insensitive) followed by the end of the heading, whitespace
     * or {@code :}, plus everything up to the next heading of the same or higher level, or the
     * end of the body. Lines inside fenced code blocks are never headings. Returns {@code body}
     * unchanged if it has no private section, otherwise the remaining text with trailing
     * whitespace stripped.
     */
    public static String stripPrivate(String body) {
        if (body == null || body.isEmpty()) return body;
        String[] lines = body.split("\n", -1);
        StringBuilder sb = new StringBuilder(body.length());
        boolean removed = false;
        int privateLevel = 0; // level of the open private heading, 0 = not in a private section
        String fence = null;
        for (String line : lines) {
            int level = 0;
            if (fence == null) {
                fence = fenceOpening(line);
                if (fence == null) level = headingLevel(line);
            } else if (line.strip().startsWith(fence)) {
                fence = null;
            }
            if (level > 0 && privateLevel > 0 && level <= privateLevel) privateLevel = 0;
            if (level > 0 && privateLevel == 0 && isPrivateHeading(line, level)) privateLevel = level;
            if (privateLevel > 0) {
                removed = true;
            } else {
                sb.append(line).append('\n');
            }
        }
        if (!removed) return body;
        return sb.toString().stripTrailing();
    }

    /**
     * Char offset of the first private-section heading in {@code body} (see
     * {@link #stripPrivate}), or {@code -1} if there is none.
     */
    public static int privateHeadingOffset(String body) {
        if (body == null || body.isEmpty()) return -1;
        int offset = 0;
        String fence = null;
        for (String line : body.split("\n", -1)) {
            if (fence == null) {
                fence = fenceOpening(line);
                int level = fence == null ? headingLevel(line) : 0;
                if (level > 0 && isPrivateHeading(line, level)) return offset;
            } else if (line.strip().startsWith(fence)) {
                fence = null;
            }
            offset += line.length() + 1;
        }
        return -1;
    }

    private static boolean isPrivateHeading(String line, int level) {
        String text = line.substring(level + 1).strip().toLowerCase(Locale.ROOT);
        for (String keyword : PRIVATE_KEYWORDS) {
            if (!text.startsWith(keyword)) continue;
            if (text.length() == keyword.length()) return true;
            char next = text.charAt(keyword.length());
            if (next == ':' || Character.isWhitespace(next)) return true;
        }
        return false;
    }

    /** The fence marker ({@code ```} or {@code ~~~}) if {@code line} opens a fenced code block. */
    private static String fenceOpening(String line) {
        String s = line.strip();
        if (s.startsWith("```")) return "```";
        if (s.startsWith("~~~")) return "~~~";
        return null;
    }

    /**
     * Result of splitting a body into its leading H1 title (if any) and the remainder.
     *
     * @param title present only if {@code body} starts with {@code "# "}; the trimmed text of
     *              that first line (without the {@code "# "} prefix)
     * @param remainder everything after the title line, with leading whitespace stripped
     *                  (tolerates a missing blank line after the title); equal to the original
     *                  body when no title line was found
     */
    public record TitleAndBody(String title, String remainder) {}

    /** Extracts a leading {@code "# "} title line from {@code body}, if present. */
    public static TitleAndBody extractTitle(String body) {
        if (body == null) return new TitleAndBody(null, "");
        if (!body.startsWith(H1_PREFIX)) return new TitleAndBody(null, body);
        int nl = body.indexOf('\n');
        String title = (nl < 0 ? body.substring(H1_PREFIX.length()) : body.substring(H1_PREFIX.length(), nl)).trim();
        String remainder = (nl < 0 ? "" : body.substring(nl + 1)).stripLeading();
        return new TitleAndBody(title, remainder);
    }
}
