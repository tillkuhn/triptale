package net.timafe.triptale.wordpress;

import net.timafe.triptale.domain.Post;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.MarkdownStore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Choosing and labelling the Post a date range is sent to (todo 62, see
 * {@code docs/62_wordpress_publish_dialog.md}). The range always wins: whichever Post is picked
 * gets the range's days on send.
 */
public final class PostPicker {

    private PostPicker() {}

    /**
     * The Post to preselect for {@code dates}: one with exactly these days, else the one sharing
     * the most days (ties: first in {@code posts}), else empty for "create new".
     */
    public static Optional<Post> preselect(List<Post> posts, List<LocalDate> dates) {
        Set<LocalDate> range = new HashSet<>(dates);
        Post best = null;
        long bestOverlap = 0;
        for (Post post : posts) {
            if (new HashSet<>(post.entryDates()).equals(range) && !range.isEmpty()) return Optional.of(post);
            long overlap = post.entryDates().stream().filter(range::contains).count();
            if (overlap > bestOverlap) {
                best = post;
                bestOverlap = overlap;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Dropdown label, e.g. {@code Day 1–2: Nice weekend · wp 1234432} or {@code … · not sent}. */
    public static String label(Trip trip, Post post) {
        String days = days(trip, post.entryDates());
        String title = post.title() == null || post.title().isBlank() ? post.slug() : post.title();
        String wp = post.wpId() == null || post.wpId().isBlank() ? "not sent" : "wp " + post.wpId();
        return (days.isEmpty() ? "" : days + ": ") + title + " · " + wp;
    }

    /**
     * {@code dates} as trip days with consecutive runs collapsed, e.g. {@code Day 1–2, 4};
     * plain dates if the trip has no start date; empty for no dates.
     */
    public static String days(Trip trip, List<LocalDate> dates) {
        if (dates.isEmpty()) return "";
        List<LocalDate> sorted = dates.stream().sorted().toList();
        List<String> runs = new ArrayList<>();
        LocalDate runStart = sorted.getFirst();
        LocalDate prev = runStart;
        for (LocalDate d : sorted.subList(1, sorted.size())) {
            if (!d.equals(prev.plusDays(1))) {
                runs.add(run(trip, runStart, prev));
                runStart = d;
            }
            prev = d;
        }
        runs.add(run(trip, runStart, prev));
        return (trip.startDate() == null ? "" : "Day ") + String.join(", ", runs);
    }

    private static String run(Trip trip, LocalDate from, LocalDate to) {
        String start = day(trip, from);
        return from.equals(to) ? start : start + "–" + day(trip, to);
    }

    private static String day(Trip trip, LocalDate date) {
        Long n = trip.dayNumber(date);
        return n == null ? date.toString() : String.valueOf(n);
    }

    /** Title prefilled for a new Post, e.g. {@code Alps 2025: Day 1–2}. */
    public static String defaultTitle(Trip trip, List<LocalDate> dates) {
        String days = days(trip, dates);
        return days.isEmpty() ? trip.name() : trip.name() + ": " + days;
    }

    /** The warning shown when sending would change {@code post}'s days, or empty if it wouldn't. */
    public static Optional<String> daysChange(Trip trip, Post post, List<LocalDate> dates) {
        if (new HashSet<>(post.entryDates()).equals(new HashSet<>(dates))) return Optional.empty();
        String before = days(trip, post.entryDates());
        return Optional.of("Days change: " + (before.isEmpty() ? "none" : before) + " → " + days(trip, dates));
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
