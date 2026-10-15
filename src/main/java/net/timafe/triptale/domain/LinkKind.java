package net.timafe.triptale.domain;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * The curated verbs an entry link can have, stored as {@code url_<key>} in frontmatter. Declared
 * alphabetically by key, matching display/export order ({@link #ORDER}). Keys outside this list are
 * still preserved on load/save, see {@link #emojiFor}.
 */
public enum LinkKind {
    CHILL("chill", "😎", "Chill at"),
    DIVE("dive", "🐠", "Dive at"),
    DRINK("drink", "🍺", "Drink at"),
    EAT("eat", "🍲", "Eat at"),
    EXPLORE("explore", "🔭", "Explore"),
    LISTEN("listen", "🎵", "Listen to"),
    SLEEP("sleep", "🛏", "Sleep at"),
    TRACK("track", "🗺", "Track tour"),
    VISIT("visit", "🏛", "Visit"),
    WATCH("watch", "🎬", "Watch");

    /** Frontmatter key prefix, e.g. {@code url_track}. */
    public static final String KEY_PREFIX = "url_";
    /** Emoji for verbs that are not in this enum (hand-edited or from a newer app version). */
    public static final String UNKNOWN_EMOJI = "🔗";

    /** Alphabetical by verb, known and unknown kinds alike. */
    public static final Comparator<String> ORDER = Comparator.naturalOrder();

    private final String key;
    private final String emoji;
    private final String label;

    LinkKind(String key, String emoji, String label) {
        this.key = key;
        this.emoji = emoji;
        this.label = label;
    }

    public String key()   { return key; }
    public String emoji() { return emoji; }
    public String label() { return label; }

    public static Optional<LinkKind> of(String key) {
        for (LinkKind k : values()) {
            if (k.key.equals(key)) return Optional.of(k);
        }
        return Optional.empty();
    }

    public static String emojiFor(String key) {
        return of(key).map(LinkKind::emoji).orElse(UNKNOWN_EMOJI);
    }

    /** "Sleep at" for known kinds, the capitalized verb ("Tour") otherwise. */
    public static String labelFor(String key) {
        return of(key).map(LinkKind::label)
                .orElseGet(() -> key.isEmpty() ? key : key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1));
    }
}
