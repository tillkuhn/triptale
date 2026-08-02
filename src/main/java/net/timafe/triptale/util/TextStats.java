package net.timafe.triptale.util;

/**
 * Simple text statistics helpers, e.g. word counting for the "Tales" label.
 */
public final class TextStats {

    private TextStats() {}

    /**
     * Counts whitespace-separated words in {@code text}. Blank or {@code null} input yields 0.
     */
    public static int wordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }
}
