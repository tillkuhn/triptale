package net.timafe.triptale.wordpress;

import net.timafe.triptale.domain.Post;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.MarkdownStore;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Choosing and labelling a trip's Posts (todos 62, 85, see {@code docs/85_post_office_posts_as_first_class_chapters.md}).
 */
public final class PostPicker {

    private PostPicker() {}

    /** The Post to preselect for {@code date}: the first whose range contains it, else empty. */
    public static Optional<Post> preselect(List<Post> posts, LocalDate date) {
        return posts.stream().filter(p -> p.contains(date)).findFirst();
    }

    /** Dropdown label, e.g. {@code Day 1-2: Nice weekend} or {@code ... · wp 1234432}. */
    public static String label(Trip trip, Post post) {
        String title = post.title() == null || post.title().isBlank() ? post.slug() : post.title();
        String days = days(trip, post.from(), post.to());
        String wp = post.wpId() == null || post.wpId().isBlank() ? "" : " · wp " + post.wpId();
        return (days.isEmpty() ? "" : days + ": ") + title + wp;
    }

    /** {@code from}..{@code to} as trip days, e.g. {@code Day 1-2}; plain dates if the trip has no start date. */
    public static String days(Trip trip, LocalDate from, LocalDate to) {
        if (from == null || to == null) return "";
        String start = day(trip, from);
        String range = from.equals(to) ? start : start + "–" + day(trip, to);
        return (trip.startDate() == null ? "" : "Day ") + range;
    }

    /** The trip day number of {@code date}, or its ISO date if the trip has no start date. */
    public static String day(Trip trip, LocalDate date) {
        Long n = trip.dayNumber(date);
        return n == null ? date.toString() : String.valueOf(n);
    }

    /** Title prefilled for a new Post, e.g. {@code Alps 2025: Day 1-2}. */
    public static String defaultTitle(Trip trip, LocalDate from, LocalDate to) {
        String days = days(trip, from, to);
        return days.isEmpty() ? trip.name() : trip.name() + ": " + days;
    }

    /**
     * Slug for a new Post titled {@code title}, with {@code -2}, {@code -3}, … if already taken.
     * Compared ignoring case, as macOS file names are.
     */
    public static String uniqueSlug(String title, Collection<String> taken) {
        Set<String> lower = new HashSet<>();
        taken.forEach(t -> lower.add(t.toLowerCase(Locale.ROOT)));
        String base = MarkdownStore.postSlug(title);
        String slug = base;
        for (int i = 2; lower.contains(slug.toLowerCase(Locale.ROOT)); i++) slug = base + "-" + i;
        return slug;
    }

    /** The post in the WordPress.com block editor. */
    public static String editorUrl(String site, String id) {
        return "https://wordpress.com/post/" + site + "/" + id;
    }
}
