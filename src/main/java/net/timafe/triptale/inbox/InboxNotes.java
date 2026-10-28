package net.timafe.triptale.inbox;

import net.timafe.triptale.util.Markdown;

import java.util.regex.Pattern;

/**
 * Appends a captured note as a bullet inside the tale's private section (todo 76) — Octobot
 * captures are personal/raw by nature, so they land in the private block instead of a separate
 * public heading. Reuses an existing private section ({@link Markdown#privateHeadingOffset}) if
 * there is one, inserting inside its block ({@link Markdown#privateBlockEnd}) rather than always
 * at the end of the tale (a private section need not be the last thing in it); otherwise creates
 * a new {@link Markdown#PRIVATE_DEFAULT_HEADING} section at the end — the same default heading
 * "🔒 Add Private Notes" uses, one shared concept instead of two. The note's own newlines are
 * collapsed into a single line (a multi-line Telegram message becomes one bullet) and suffixed
 * with {@link #SUFFIX} so its origin stays visible in the raw text. A pure string function so it
 * works the same whether the target entry is the one currently open in the main form (operating
 * on its text area) or loaded fresh from disk for a different day — see
 * {@code docs/68_mobile_inbox.md} and {@code docs/76_private_notes_octobot_inbox.md} for
 * background.
 */
public final class InboxNotes {

    /** Marks an Octobot-added bullet's origin, e.g. {@code "* Saw a waterfall — added by Octobot 🐙"}. */
    private static final String SUFFIX = " — added by Octobot \uD83D\uDC19";
    private static final Pattern BULLET_LINE = Pattern.compile("^[*+-]\\s");

    private InboxNotes() {
    }

    public static String append(String body, String note) {
        String bullet = "* " + collapse(note) + SUFFIX;
        int headingOffset = Markdown.privateHeadingOffset(body);
        if (headingOffset < 0) {
            String trimmed = body == null ? "" : body.stripTrailing();
            String addition = (trimmed.isEmpty() ? "" : "\n\n") + Markdown.PRIVATE_DEFAULT_HEADING + "\n\n" + bullet;
            return trimmed + addition;
        }
        int blockEnd = Markdown.privateBlockEnd(body);
        String head = body.substring(0, blockEnd).stripTrailing();
        String tail = body.substring(blockEnd);
        String separator = BULLET_LINE.matcher(lastLine(head)).find() ? "\n" : "\n\n";
        String rebuilt = head + separator + bullet;
        return tail.isBlank() ? rebuilt : rebuilt + "\n\n" + tail;
    }

    /** Collapses any run of whitespace (including newlines) into a single space. */
    private static String collapse(String note) {
        return note == null ? "" : note.strip().replaceAll("\\s+", " ");
    }

    private static String lastLine(String text) {
        int i = text.lastIndexOf('\n');
        return i < 0 ? text : text.substring(i + 1);
    }
}
