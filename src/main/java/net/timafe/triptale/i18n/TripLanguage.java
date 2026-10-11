package net.timafe.triptale.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * The languages exported trip content can be written in (todo 30). The app UI itself stays
 * English; only text that ends up in an export follows the trip language.
 */
public enum TripLanguage {
    EN("en", "English"),
    DE("de", "Deutsch");

    public static final TripLanguage DEFAULT = EN;

    private static final Logger log = LoggerFactory.getLogger(TripLanguage.class);

    private final String code;
    private final String displayName;

    TripLanguage(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /** ISO 639-1 code as stored in {@code settings.yml}, e.g. {@code de}. */
    public String code() { return code; }
    /** Name in the language itself, e.g. "Deutsch". */
    public String displayName() { return displayName; }
    public Locale locale() { return Locale.forLanguageTag(code); }

    /** The language for {@code code}; blank means {@link #DEFAULT}, unknown values too (with a warning). */
    public static TripLanguage fromCode(String code) {
        if (code == null || code.isBlank()) return DEFAULT;
        for (TripLanguage l : values()) {
            if (l.code.equalsIgnoreCase(code.trim())) return l;
        }
        log.warn("Unknown trip language '{}', falling back to {}", code, DEFAULT.code);
        return DEFAULT;
    }
}
