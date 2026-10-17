package net.timafe.triptale.domain;

/**
 * A day's weather (todo 65): whole-degree Celsius min/max and a raw WMO {@code weather_code}
 * (bucketed into a label/emoji by {@link WeatherCode} only at export/display time, never
 * stored as text). Fetched automatically from Open-Meteo once an entry has a start point.
 * Stored in frontmatter as one packed scalar, {@code "min;max;code[;place]"} — weather is
 * sugar, not load-bearing like coordinates, so it doesn't earn a key per field. Semicolon,
 * not comma: a comma is the German decimal separator and also turns up inside place names
 * ({@code "Essen, DE"}), which made the packed value confusing to read by eye.
 *
 * @param place the entry's own {@code start_place} echoed in purely for readability when reading
 *              the weather line standalone; Open-Meteo itself never returns a place name, and
 *              this is not re-validated against {@code start_place} later (the two can drift if
 *              the place is edited afterwards — acceptable since this field is sugar)
 */
public record Weather(int tempMin, int tempMax, int code, String place) {

    /**
     * Parses a frontmatter value. Tolerant: a value that doesn't look like
     * {@code "min;max;code[;place]"} is treated as absent ({@code null}) rather than thrown,
     * consistent with other tolerated-but-not-enforced frontmatter values in this app.
     */
    public static Weather parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // limit=4 keeps a comma inside the place field intact, e.g. "15;22;1;Essen, DE"
        String[] parts = raw.strip().split(";", 4);
        if (parts.length < 3) return null;
        try {
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            int code = Integer.parseInt(parts[2].trim());
            String place = parts.length == 4 ? parts[3].trim() : null;
            return new Weather(min, max, code, place == null || place.isEmpty() ? null : place);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The frontmatter value: {@code "min;max;code"}, with {@code ;place} appended when present. */
    public String toRaw() {
        return tempMin + ";" + tempMax + ";" + code + (place == null ? "" : ";" + place);
    }
}
