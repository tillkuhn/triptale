package net.timafe.triptale.util;

import java.util.Locale;

/**
 * Human-readable byte counts for the UI ("312 KB", "4.5 MB", "1.2 GB"). Decimal units (1 KB =
 * 1000 bytes), {@link Locale#ROOT} formatting. Rough on purpose: enough to tell a small file from a
 * big one, not an exact figure. No JavaFX imports.
 */
public final class ByteSizes {

    private ByteSizes() {}

    public static String humanize(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("negative size: " + bytes);
        if (bytes < 1_000_000) return Math.max(1, Math.round(bytes / 1000.0)) + " KB";
        if (bytes < 1_000_000_000) return String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0);
        return String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0);
    }
}
