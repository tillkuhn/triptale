package net.timafe.triptale.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class Slugs {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s_]+");
    private static final Pattern EDGES = Pattern.compile("(^-+)|(-+$)");
    private static final Pattern DASHES = Pattern.compile("-{2,}");

    private Slugs() {}

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("slug input must not be blank");
        }
        String nowhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = NON_LATIN.matcher(normalized).replaceAll("");
        slug = DASHES.matcher(slug).replaceAll("-");
        slug = EDGES.matcher(slug).replaceAll("");
        slug = slug.toLowerCase();
        if (slug.isBlank()) {
            throw new IllegalArgumentException("slug is empty after normalization: " + input);
        }
        return slug;
    }
}
