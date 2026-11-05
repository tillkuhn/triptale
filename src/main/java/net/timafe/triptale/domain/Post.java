package net.timafe.triptale.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A chapter of a trip (todos 60, 85): a date range {@code from}..{@code to} (inclusive, plain
 * dates that need not match entry files; days without an entry are skipped on export) with a
 * {@code title} and {@code intro} that head the chapter in every export. It need not go to
 * WordPress; {@code wpId}/{@code wpModified} are set only after a successful send, both null
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
        Instant wpModified
) {
    /** A new, never-sent Post. */
    public static Post of(String slug, String title, String intro, LocalDate from, LocalDate to) {
        return new Post(slug, title, intro, from, to, null, null);
    }

    /** This Post with {@code wpId}/{@code wpModified} set after a successful send. */
    public Post withWordPress(String wpId, Instant wpModified) {
        return new Post(slug, title, intro, from, to, wpId, wpModified);
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
