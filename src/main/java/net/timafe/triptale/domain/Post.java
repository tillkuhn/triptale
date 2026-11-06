package net.timafe.triptale.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A chapter of a trip (todos 60, 85): a date range {@code from}..{@code to} (inclusive, plain
 * dates that need not match entry files; days without an entry are skipped on export) with a
 * {@code title} and {@code intro} that head the chapter in every export. It need not go to
 * WordPress; {@code tags} are WordPress tag names, sent with a new draft; on an update only if the user opts in
 * (they replace the tags on WordPress); {@code wpId}/{@code wpModified} are set only after a successful send, both null
 * means "never sent".
 *
 * <p>Lives at {@code <trip>/posts/<slug>.md} - a Post is scoped to one trip, identified within it
 * by {@code slug} (derived once from {@code title} via {@code Slugs.toSlug()}, immutable like a
 * trip slug: renaming the title does not rename the file).
 */
public record Post(
        String slug,
        String title,
        String intro,
        LocalDate from,
        LocalDate to,
        String wpId,
        Instant wpModified,
        List<String> tags
) {
    public Post {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** A Post without tags. */
    public Post(String slug, String title, String intro, LocalDate from, LocalDate to, String wpId, Instant wpModified) {
        this(slug, title, intro, from, to, wpId, wpModified, List.of());
    }

    /** A new, never-sent Post. */
    public static Post of(String slug, String title, String intro, LocalDate from, LocalDate to) {
        return new Post(slug, title, intro, from, to, null, null);
    }

    /** This Post with {@code wpId}/{@code wpModified} set after a successful send. */
    public Post withWordPress(String wpId, Instant wpModified) {
        return new Post(slug, title, intro, from, to, wpId, wpModified, tags);
    }

    /** This Post with the given WordPress tags (names, free-form). */
    public Post withTags(List<String> tags) {
        return new Post(slug, title, intro, from, to, wpId, wpModified, tags);
    }

    /** True if {@code date} lies within the range (inclusive). */
    public boolean contains(LocalDate date) {
        return date != null && from != null && to != null && !date.isBefore(from) && !date.isAfter(to);
    }

    /** The days of {@code entryDates} that fall into this range, in the given order. */
    public List<LocalDate> datesIn(List<LocalDate> entryDates) {
        return entryDates.stream().filter(this::contains).toList();
    }
}
