package net.timafe.triptale.i18n;

import net.timafe.triptale.domain.LinkKind;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Words, dates and numbers for exported trip content in one {@link TripLanguage} (todo 30).
 * Backed by {@code i18n/export.properties} (English base) and per-language files such as
 * {@code i18n/export_de.properties}; a key missing from a language falls back to English.
 */
public final class ExportTexts {

    private static final String BUNDLE = "i18n.export";
    // Without this, a missing export_en.properties would fall back to the JVM default locale (German on a German Mac)
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static final String ORDINAL = "{ordinal}";

    private final TripLanguage language;
    private final ResourceBundle bundle;

    private ExportTexts(TripLanguage language) {
        this.language = language;
        this.bundle = ResourceBundle.getBundle(BUNDLE, language.locale(), ExportTexts.class.getClassLoader(), NO_FALLBACK);
    }

    public static ExportTexts of(TripLanguage language) {
        return new ExportTexts(language);
    }

    public TripLanguage language() { return language; }

    /** The text for {@code key}, e.g. {@code stats.distance} → "Distanz"; an unknown key is a bug and throws. */
    public String word(String key) {
        return bundle.getString(key);
    }

    /** {@code key.one} for a count of 1, {@code key.other} otherwise, e.g. "day" / "days". */
    public String plural(String key, long count) {
        return word(key + (count == 1 ? ".one" : ".other"));
    }

    /** {@code link.<verb>} if translated, else the English {@link LinkKind} label ("Sleep at", "Tour"). */
    public String linkLabel(String verb) {
        String key = "link." + verb;
        return bundle.containsKey(key) ? bundle.getString(key) : LinkKind.labelFor(verb);
    }

    /** Full weekday name, e.g. "Donnerstag". */
    public String weekday(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("EEEE", language.locale()));
    }

    /**
     * The date per {@code date.long}, e.g. "23. Juli 2026" or "July 23rd 2026". A {@code {ordinal}}
     * token becomes the day with its English suffix, which {@link DateTimeFormatter} can't produce.
     */
    public String longDate(LocalDate date) {
        String pattern = word("date.long").replace(ORDINAL, "'" + date.getDayOfMonth() + ordinalSuffix(date.getDayOfMonth()) + "'");
        return date.format(DateTimeFormatter.ofPattern(pattern, language.locale()));
    }

    /** One decimal in the trip locale, e.g. "12,5" in German. */
    public String distance(double km) {
        return String.format(language.locale(), "%.1f", km);
    }

    /** Whole meters in the trip locale, without grouping. */
    public String altitude(double meters) {
        return String.format(language.locale(), "%.0f", meters);
    }

    static String ordinalSuffix(int day) {
        if (day >= 11 && day <= 13) return "th";
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }
}
