package net.timafe.triptale.inbox;

/**
 * Appends a captured note to an entry's tale body under a shared {@code ## Inbox} heading
 * (todo 68): the heading is added once, repeat notes just land below it. A pure string function
 * so it works the same whether the target entry is the one currently open in the main form
 * (operating on its text area) or loaded fresh from disk for a different day — see
 * {@code docs/68_mobile_inbox.md} for why the body (not a frontmatter key) is the v1 landing spot.
 */
public final class InboxNotes {

    public static final String HEADING = "## Inbox";

    private InboxNotes() {
    }

    public static String append(String body, String note) {
        String trimmed = body == null ? "" : body.stripTrailing();
        String addition = trimmed.contains(HEADING) ? note : HEADING + "\n\n" + note;
        return trimmed.isEmpty() ? addition : trimmed + "\n\n" + addition;
    }
}
