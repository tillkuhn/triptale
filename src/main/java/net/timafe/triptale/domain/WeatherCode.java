package net.timafe.triptale.domain;

/**
 * Buckets Open-Meteo's WMO {@code weather_code} values (todo 65) into a small set of conditions
 * for display, each with an emoji; the raw numeric code is what's actually stored (see
 * {@link Weather}), this mapping only matters at export/display time. An unrecognized code falls
 * back to {@link #CLOUDY} rather than erroring.
 */
public enum WeatherCode {
    CLEAR("clear", "☀️"),
    PARTLY_CLOUDY("partly_cloudy", "🌤️"),
    CLOUDY("cloudy", "☁️"),
    FOG("fog", "🌫️"),
    DRIZZLE("drizzle", "🌦️"),
    RAIN("rain", "🌧️"),
    SNOW("snow", "❄️"),
    THUNDERSTORM("thunderstorm", "⛈️");

    private final String key;
    private final String emoji;

    WeatherCode(String key, String emoji) {
        this.key = key;
        this.emoji = emoji;
    }

    /** Lowercase key used for the {@code weather.<key>} i18n label, e.g. {@code "partly_cloudy"}. */
    public String key()   { return key; }
    public String emoji() { return emoji; }

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
