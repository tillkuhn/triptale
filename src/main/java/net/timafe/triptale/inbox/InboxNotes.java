package net.timafe.triptale.inbox;

import net.timafe.triptale.domain.LinkKind;
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
 * with an origin marker so it stays visible in the raw text — plain {@code "added by Octobot 🐙"},
 * or {@code "added and eat-linked by Octobot 🐙"} when the Add also created a {@code url_<verb>}
 * weblink (todo 77, {@code MainController.applyWeblink}), naming the verb so the bullet and the
 * resulting Weblink entry are visibly connected. A pure string function so it works the same
 * whether the target entry is the one currently open in the main form (operating on its text
 * area) or loaded fresh from disk for a different day — see {@code docs/68_mobile_inbox.md},
 * {@code docs/76_private_notes_octobot_inbox.md} and {@code docs/77_inbox_create_weblink.md} for
 * background.
 */
public final class InboxNotes {

    private static final String ORIGIN = "added by Octobot \uD83D\uDC19";
    private static final Pattern BULLET_LINE = Pattern.compile("^[*+-]\\s");

    private InboxNotes() {
    }

    public static String append(String body, String note) {
        return append(body, note, null);
    }

    /** Overload used when the Add also created a weblink (todo 77) — {@code verb} is the link's kind, or null if none. */
    public static String append(String body, String note, LinkKind verb) {
        String origin = verb == null ? ORIGIN : "added and " + verb.key() + "-linked by Octobot \uD83D\uDC19";
        String bullet = "* " + collapse(note) + " — " + origin;
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
