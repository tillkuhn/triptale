package net.timafe.triptale.domain;

/**
 * Buckets Open-Meteo's WMO {@code weather_code} values (todo 65) into a small set of conditions
 * for display, each with an emoji; the raw numeric code is what's actually stored (see
 * {@link Weather}), this mapping only matters at export/display time. An unrecognized code falls
 * back to {@link #CLOUDY} rather than erroring.
 */
public enum WeatherCode {
    // All glyphs below except SNOW are Supplementary Multilingual Plane pictographs, deliberately
    // — BMP symbols like plain ☀/❄/☁️/⛈️ also exist as a plain monochrome glyph in the UI font
    // (Segoe UI/Helvetica Neue/Arial), and JavaFX's font fallback picks that over the color
    // emoji font regardless of a trailing \uFE0F variation selector (which, per the Unicode
    // spec, shouldn't even be needed for glyphs that already default to emoji presentation —
    // JavaFX's text renderer chokes on it anyway, drawing a "tofu" box). SMP codepoints are
    // guaranteed absent from the UI font, so fallback to the color emoji font is forced rather
    // than a matter of selector support. CLEAR uses "sun with face" (1F31E) rather than the
    // plain sun (BMP 2600) for exactly this reason. SNOW tried the same move to "cloud with
    // snow" (1F328) but that reads as just another cloud glyph next to CLOUDY/RAIN/etc. — the
    // plain monochrome snowflake (BMP 2744, no \uFE0F) is actually the more legible choice here,
    // so it's a deliberate exception rather than an oversight.
    CLEAR("clear", "🌞", 0),
    PARTLY_CLOUDY("partly_cloudy", "🌤", 2),
    CLOUDY("cloudy", "🌥", 3),
    FOG("fog", "🌫", 45),
    DRIZZLE("drizzle", "🌦", 53),
    RAIN("rain", "🌧", 61),
    SNOW("snow", "❄", 71),
    THUNDERSTORM("thunderstorm", "🌩", 95);

    private final String key;
    private final String emoji;
    private final int wmoCode;

    WeatherCode(String key, String emoji, int wmoCode) {
        this.key = key;
        this.emoji = emoji;
        this.wmoCode = wmoCode;
    }

    /** Lowercase key used for the {@code weather.<key>} i18n label, e.g. {@code "partly_cloudy"}. */
    public String key()   { return key; }
    public String emoji() { return emoji; }

    /**
     * A raw WMO code that maps back to this bucket via {@link #fromWmo}, for when a condition is
     * picked by hand (e.g. the Coordinator dialog's weather dropdown) rather than fetched — the
     * bucket is all a manual pick can express, so any representative member of it round-trips
     * losslessly through every display/export path, which only ever buckets the raw code anyway.
     */
    public int wmoCode() { return wmoCode; }

    /** Maps a raw WMO weather code to its bucket; an unrecognized code is treated as {@link #CLOUDY}. */
    public static WeatherCode fromWmo(int code) {
        return switch (code) {
            case 0 -> CLEAR;
            case 1, 2 -> PARTLY_CLOUDY;
            case 3 -> CLOUDY;
            case 45, 48 -> FOG;
            case 51, 53, 55, 56, 57 -> DRIZZLE;
            case 61, 63, 65, 66, 67, 80, 81, 82 -> RAIN;
            case 71, 73, 75, 77, 85, 86 -> SNOW;
            case 95, 96, 99 -> THUNDERSTORM;
            default -> CLOUDY;
        };
    }
}
