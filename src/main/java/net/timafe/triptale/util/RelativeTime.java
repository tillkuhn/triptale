package net.timafe.triptale.util;

import java.time.Duration;
import java.time.Instant;

/**
 * Formats an {@link Instant} as a human-readable "time ago" string, e.g. "just now",
 * "5 minutes ago", "2 days ago". Used for the "Tales" label's last-updated indicator.
 */
public final class RelativeTime {

    private RelativeTime() {}

    public static String ago(Instant instant, Instant now) {
        Duration duration = Duration.between(instant, now);
        long seconds = Math.max(0, duration.getSeconds());

        if (seconds < 10) return "just now";
        if (seconds < 60) return seconds + " seconds ago";

        long minutes = seconds / 60;
        if (minutes < 60) return plural(minutes, "minute");

        long hours = minutes / 60;
        if (hours < 24) return plural(hours, "hour");

        long days = hours / 24;
        return plural(days, "day");
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s") + " ago";
    }
}
