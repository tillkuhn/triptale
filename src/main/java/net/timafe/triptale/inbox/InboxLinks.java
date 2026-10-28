package net.timafe.triptale.inbox;

import net.timafe.triptale.domain.Link;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure helpers for turning an inbox capture's first URL into a weblink suggestion (todo 77):
 * detecting it, deriving a default name from the rest of the text, and guessing which
 * {@link net.timafe.triptale.domain.LinkKind} verb the text hints at. No JavaFX, no I/O — kept
 * separate from {@link InboxNotes} since that one is about the private-notes bullet, not the
 * structured weblink extracted from the same capture.
 */
public final class InboxLinks {

    /** Splits text into word-or-whitespace runs, same tokenizer the inbox UI uses to spot URLs. */
    private static final Pattern TOKENS = Pattern.compile("\\S+|\\s+");

    private InboxLinks() {
    }

    /** The first http(s) URL token found in {@code text}, left-to-right; empty if there's none. */
    public static Optional<String> firstUrl(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        Matcher m = TOKENS.matcher(text);
        while (m.find()) {
            String token = m.group();
            if (Link.isHttpUrl(token)) return Optional.of(token);
        }
        return Optional.empty();
    }

    /**
     * {@code text} with the first occurrence of {@code url} removed and whitespace (including
     * newlines) collapsed into single spaces, trimmed — the default weblink name, e.g.
     * {@code "Beldibi Turkish Bath\nhttps://…"} becomes {@code "Beldibi Turkish Bath"}.
     */
    public static String nameWithoutUrl(String text, String url) {
        if (text == null) return "";
        String withoutUrl = (url == null || url.isBlank()) ? text : text.replaceFirst(Pattern.quote(url), " ");
        return withoutUrl.strip().replaceAll("\\s+", " ").strip();
    }

    /**
     * Guesses a verb from {@code text}: the earliest (left-to-right) whole-word match of any
     * {@code untakenKeys} entry, case-insensitive — e.g. "Dive and eat at the harbor" picks
     * {@code dive} since it appears first. Empty if nothing matches or no key is untaken.
     */
    public static Optional<String> guessVerb(String text, Collection<String> untakenKeys) {
        if (text == null || text.isBlank() || untakenKeys.isEmpty()) return Optional.empty();
        String lower = text.toLowerCase(Locale.ROOT);
        String best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (String key : untakenKeys) {
            if (key == null || key.isBlank()) continue;
            Matcher m = Pattern.compile("\\b" + Pattern.quote(key.toLowerCase(Locale.ROOT)) + "\\b").matcher(lower);
            if (m.find() && m.start() < bestIndex) {
                bestIndex = m.start();
                best = key;
            }
        }
        return Optional.ofNullable(best);
    }
}
