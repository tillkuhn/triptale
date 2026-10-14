package net.timafe.triptale.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A WordPress-bound grouping of a trip's days (todo 60): {@code title}/{@code intro} become the
 * post's heading and lead paragraph on export, {@code entryDates} the days it covers (sorted
 * ascending; a {@code has:} wikilink to a missing entry file is dropped by {@code MarkdownStore}
 * with a warning, never surfaced here). {@code wpId}/{@code wpModified} are set only after a
 * successful send to WordPress; both null means "never sent".
 *
 * <p>Lives at {@code <trip>/posts/<slug>.md} — a Post is scoped to one trip, identified within it
 * by {@code slug} (derived once from {@code title} via {@code Slugs.toSlug()}, immutable like a
 * trip slug: renaming the title does not rename the file).
 */
public record Post(
        String slug,
        String title,
        String intro,
        List<LocalDate> entryDates,
        String wpId,
        Instant wpModified
) {
    public Post {
        entryDates = entryDates == null ? List.of() : List.copyOf(entryDates);
    }

    /** A new, never-sent Post. */
    public static Post of(String slug, String title, String intro, List<LocalDate> entryDates) {
        return new Post(slug, title, intro, entryDates, null, null);
    }

    /** This Post with {@code wpId}/{@code wpModified} set after a successful send. */
    public Post withWordPress(String wpId, Instant wpModified) {
        return new Post(slug, title, intro, entryDates, wpId, wpModified);
    }
}
