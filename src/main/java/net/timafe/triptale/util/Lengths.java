package net.timafe.triptale.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats the entry form's distance (km) and altitude (m) fields.
 * <p>
 * Input is a non-negative number ({@code .} or {@code ,} as decimal separator), optionally
 * followed by {@code km} or {@code m} (case-insensitive, space optional). A bare number is in the
 * field's default unit. Values are converted to the field's unit and rounded: distance to 2
 * decimals, altitude to whole metres. Formatting yields {@code "101.64 km"} / {@code "493 m"}.
 */
public final class Lengths {

    private static final Pattern INPUT =
            Pattern.compile("(\\d+(?:[.,]\\d*)?|[.,]\\d+)\\s*(km|m)?", Pattern.CASE_INSENSITIVE);

    private Lengths() {}

    private enum Unit {
        KM("km", 1000, 2),
        M("m", 1, 0);

        final String symbol;
        final int metres;
        final int decimals;

        Unit(String symbol, int metres, int decimals) {
            this.symbol = symbol;
            this.metres = metres;
            this.decimals = decimals;
        }
    }

    /**
     * Parses a distance into kilometres. Blank yields {@code null}.
     *
     * @throws IllegalArgumentException if {@code text} is neither blank nor a valid length
     */
    public static Double parseDistanceKm(String text) {
        return parse(text, Unit.KM);
    }

    /**
     * Parses an altitude into metres. Blank yields {@code null}.
     *
     * @throws IllegalArgumentException if {@code text} is neither blank nor a valid length
     */
    public static Double parseAltitudeM(String text) {
        return parse(text, Unit.M);
    }

    /** {@code "101.64 km"}, or {@code ""} for {@code null}. */
    public static String formatDistance(Double km) {
        return format(km, Unit.KM);
    }

    /** {@code "493 m"}, or {@code ""} for {@code null}. */
    public static String formatAltitude(Double m) {
        return format(m, Unit.M);
    }

    private static Double parse(String text, Unit target) {
        if (text == null || text.isBlank()) return null;
        Matcher m = INPUT.matcher(text.strip());
        if (!m.matches()) {
            throw new IllegalArgumentException("Not a number with optional km or m: " + text.strip());
        }
        BigDecimal value = new BigDecimal(normalizeNumber(m.group(1)));
        Unit given = m.group(2) == null ? target : Unit.valueOf(m.group(2).toUpperCase(Locale.ROOT));
        if (given != target) {
            value = value.multiply(BigDecimal.valueOf(given.metres))
                    .divide(BigDecimal.valueOf(target.metres));
        }
        return value.setScale(target.decimals, RoundingMode.HALF_UP).doubleValue();
    }

    private static String normalizeNumber(String number) {
        String s = number.replace(',', '.');
        if (s.startsWith(".")) s = "0" + s;
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String format(Double value, Unit unit) {
        if (value == null) return "";
        String number = BigDecimal.valueOf(value)
                .setScale(unit.decimals, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
        return number + " " + unit.symbol;
    }
}
