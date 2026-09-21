package net.timafe.triptale.ui;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display-string helpers shared by the main window and the dialog classes. */
public final class UiText {

    private UiText() {
    }

    /** Renders a path with the user's home directory prefix collapsed to {@code ~}. */
    public static String homeRelative(Path p) {
        String home = System.getProperty("user.home", "");
        String s = p.toString();
        return (!home.isEmpty() && s.startsWith(home)) ? "~" + s.substring(home.length()) : s;
    }

    /** e.g. "Friday, Aug 21st, 2026" — friendlier than a bare ISO date for a dialog title. */
    public static String friendlyDate(LocalDate date) {
        if (date == null) return "";
        String weekday = date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH));
        String month = date.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH));
        int day = date.getDayOfMonth();
        return weekday + ", " + month + " " + day + daySuffix(day) + ", " + date.getYear();
    }

    private static String daySuffix(int day) {
        if (day >= 11 && day <= 13) return "th";
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    /** Flattens an exception chain into one line, so a wrapped JGit/IO cause isn't swallowed. */
    public static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        while (t != null) {
            if (sb.length() > 0) sb.append(" — caused by ");
            String m = t.getMessage();
            sb.append(t.getClass().getSimpleName())
                    .append(m == null ? "" : ": " + m);
            t = t.getCause();
        }
        return sb.toString();
    }

    /** True when the value is a well-formed absolute http(s) URL. */
    public static boolean isValidHttpUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            java.net.URI uri = java.net.URI.create(value.trim());
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Parses a decimal accepting both {@code .} and {@code ,} as separator; null when unparseable. */
    public static Double parseDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException nfe) {
            return null;
        }
    }
}
