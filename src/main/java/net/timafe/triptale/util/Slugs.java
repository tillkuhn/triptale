package net.timafe.triptale.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class Slugs {

    private static final Pattern WORD_SEPARATORS = Pattern.compile("[\\s_-]+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\w]");

    private Slugs() {}

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("slug input must not be blank");
        }
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        StringBuilder slug = new StringBuilder();
        for (String word : WORD_SEPARATORS.split(normalized)) {
            String cleaned = NON_ALPHANUMERIC.matcher(word).replaceAll("");
            if (cleaned.isEmpty()) {
                continue;
            }
            slug.append(Character.toUpperCase(cleaned.charAt(0))).append(cleaned.substring(1));
        }
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("slug is empty after normalization: " + input);
        }
        return slug.toString();
    }
}
