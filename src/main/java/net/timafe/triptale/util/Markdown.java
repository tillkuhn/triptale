package net.timafe.triptale.util;

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
