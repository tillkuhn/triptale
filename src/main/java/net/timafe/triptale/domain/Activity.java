package net.timafe.triptale.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * The curated main activities of a day, stored lowercase as frontmatter {@code activity}
 * (todo 55). Declared alphabetically by key. Other values are tolerated and preserved, see
 * {@link #normalize}; null means "not set".
 */
public enum Activity {
    BACKPACKING("backpacking", "🎒"),
    BIKEPACKING("bikepacking", "🚵"),
    CYCLING("cycling", "🚴"),
    DIVING("diving", "🤿"),
    FERRYHOPPING("ferryhopping", "⛴"),
    HIKING("hiking", "🚶"),
    MOTORCYCLING("motorcycling", "🏍"),
    RAILTRIPPING("railtripping", "🚆"),
    RELAXING("relaxing", "🏖"),
    ROADTRIPPING("roadtripping", "🚗"),
    SIGHTSEEING("sightseeing", "🏰"),
    SKYHOPPING("skyhopping", "✈");

    /** Emoji for values that are not in this enum (hand-edited or from a newer app version). */
    public static final String UNKNOWN_EMOJI = "❓";

    private final String key;
    private final String emoji;

    Activity(String key, String emoji) {
        this.key = key;
        this.emoji = emoji;
    }

    public String key()   { return key; }
    public String emoji() { return emoji; }
    public String label() { return labelFor(key); }

    /** Case-insensitive, ignoring surrounding whitespace. */
    public static Optional<Activity> of(String value) {
        if (value == null) return Optional.empty();
        String key = value.strip().toLowerCase(Locale.ROOT);
        for (Activity a : values()) {
            if (a.key.equals(key)) return Optional.of(a);
        }
        return Optional.empty();
    }

    /** Null for blank, the lowercase key for a known activity, otherwise the stripped value as written. */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return of(value).map(Activity::key).orElse(value.strip());
    }

    public static String emojiFor(String value) {
        return of(value).map(Activity::emoji).orElse(UNKNOWN_EMOJI);
    }

    /** The value with an uppercase first letter, e.g. "Hiking"; known and unknown values alike. */
    public static String labelFor(String value) {
        return value == null || value.isEmpty() ? "" : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
