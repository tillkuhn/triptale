package net.timafe.triptale.domain;

/**
 * A day's weather (todo 65): whole-degree Celsius min/max and a raw WMO {@code weather_code}
 * (bucketed into a label/emoji by {@link WeatherCode} only at export/display time, never
 * stored as text). Fetched automatically from Open-Meteo once an entry has a start point.
 * Stored in frontmatter as one packed scalar, {@code "min;max;code;elevation[;place]"} —
 * weather is sugar, not load-bearing like coordinates, so it doesn't earn a key per field.
 * Semicolon, not comma: a comma is the German decimal separator and also turns up inside place
 * names ({@code "Essen, DE"}), which made the packed value confusing to read by eye.
 *
 * <p>{@code elevation} (todo 72) sits after {@code code}, before {@code place}: {@code
 * min;max;code} is the "what happened" core (temp + condition, always read together);
 * {@code elevation}/{@code place} are both "where" context, grouped at the tail. To keep both
 * optional tail fields unambiguously positional, {@code elevation} is never skipped once {@code
 * place} is present — it's written as an explicit empty field instead ({@code
 * "15;22;1;;Essen, DE"}) — see {@link #toRaw()}. This is a breaking change from the pre-todo-72
 * {@code "min;max;code[;place]"} shape: an old value with a place and no elevation is
 * positionally identical to a new value with an elevation and no place, so old data must be
 * migrated ({@code scripts/migrate_weather_elevation.py}) rather than auto-detected — an
 * unmigrated old value simply fails to parse (its place text isn't a valid elevation number)
 * and is tolerated as absent, same as any other unparseable value.
 *
 * @param elevation meters above sea level at the fetched point (Open-Meteo returns this for
 *                  free alongside the forecast/archive response), rounded to a whole number;
 *                  {@code null} when not recorded (entries from before todo 72, or wherever the
 *                  migration script couldn't infer it)
 * @param place the entry's own {@code start_place} echoed in purely for readability when reading
 *              the weather line standalone; Open-Meteo itself never returns a place name, and
 *              this is not re-validated against {@code start_place} later (the two can drift if
 *              the place is edited afterwards — acceptable since this field is sugar)
 */
public record Weather(int tempMin, int tempMax, int code, Integer elevation, String place) {

    /**
     * Parses a frontmatter value. Tolerant: a value that doesn't look like
     * {@code "min;max;code;elevation[;place]"} is treated as absent ({@code null}) rather than
     * thrown, consistent with other tolerated-but-not-enforced frontmatter values in this app.
     */
    public static Weather parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // limit=5 keeps a comma (or a stray ";") inside the place field intact, e.g. "15;22;1;;Essen, DE"
        String[] parts = raw.strip().split(";", 5);
        if (parts.length < 3) return null;
        try {
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            int code = Integer.parseInt(parts[2].trim());
            Integer elevation = null;
            String place = null;
            if (parts.length >= 4) {
                String elevationPart = parts[3].trim();
                elevation = elevationPart.isEmpty() ? null : Integer.parseInt(elevationPart);
            }
            if (parts.length == 5) {
                String placePart = parts[4].trim();
                place = placePart.isEmpty() ? null : placePart;
            }
            return new Weather(min, max, code, elevation, place);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The frontmatter value: {@code "min;max;code"}, with {@code ;elevation} appended when
     * present (as an explicit empty field if only {@code place} is present, so the shape stays
     * positionally unambiguous), and {@code ;place} appended when present.
     */
    public String toRaw() {
        StringBuilder sb = new StringBuilder().append(tempMin).append(';').append(tempMax).append(';').append(code);
        if (elevation != null || place != null) {
            sb.append(';').append(elevation == null ? "" : elevation);
        }
        if (place != null) {
            sb.append(';').append(place);
        }
        return sb.toString();
    }
}
