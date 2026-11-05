package net.timafe.triptale.wordpress;

import net.timafe.triptale.domain.Post;
import net.timafe.triptale.domain.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPickerTest {

    private static final LocalDate DAY1 = LocalDate.of(2025, 7, 1);
    private static final LocalDate DAY2 = DAY1.plusDays(1);
    private static final LocalDate DAY3 = DAY1.plusDays(2);
    private static final LocalDate DAY4 = DAY1.plusDays(3);

    private final Trip trip = new Trip(2025, "alps-2025", "Alps 2025", DAY1, null, "");

    private static Post post(String title, LocalDate from, LocalDate to) {
        return Post.of(title.replace(" ", ""), title, "", from, to);
    }

    @Test
    void preselectTakesFirstPostContainingTheDate() {
        Post a = post("A", DAY1, DAY2);
        Post b = post("B", DAY2, DAY4);

        assertEquals(Optional.of(a), PostPicker.preselect(List.of(a, b), DAY2));
        assertEquals(Optional.of(b), PostPicker.preselect(List.of(a, b), DAY3));
        assertEquals(Optional.empty(), PostPicker.preselect(List.of(a), DAY4));
        assertEquals(Optional.empty(), PostPicker.preselect(List.of(a), null));
    }

    @Test
    void labelShowsDaysTitleAndWordPressId() {
        assertEquals("Day 1–2: Nice weekend · wp 1234432",
                PostPicker.label(trip, post("Nice weekend", DAY1, DAY2).withWordPress("1234432", null)));
        assertEquals("Day 3: Single", PostPicker.label(trip, post("Single", DAY3, DAY3)));
    }

    @Test
    void daysUsePlainDatesWithoutTripStart() {
        Trip noStart = new Trip(2025, "x", "X", null, null, "");

        assertEquals("2025-07-01–2025-07-02", PostPicker.days(noStart, DAY1, DAY2));
        assertEquals("X", PostPicker.defaultTitle(noStart, null, null));
    }

    @Test
    void defaultTitleCombinesTripNameAndDays() {
        assertEquals("Alps 2025: Day 1–3", PostPicker.defaultTitle(trip, DAY1, DAY3));
    }

    @Test
    void datesInKeepsOnlyEntryDaysWithinTheRange() {
        assertEquals(List.of(DAY2, DAY3), post("P", DAY2, DAY4).datesIn(List.of(DAY1, DAY2, DAY3)));
    }

    @Test
    void uniqueSlugAppendsCounterIgnoringCase() {
        assertEquals("NiceWeekend", PostPicker.uniqueSlug("Nice weekend", List.of("Other")));
        assertEquals("NiceWeekend-3", PostPicker.uniqueSlug("Nice weekend", List.of("niceweekend", "NiceWeekend-2")));
    }

    @Test
    void editorUrlPointsToBlockEditor() {
        assertTrue(PostPicker.editorUrl("blog.example.com", "42").endsWith("/post/blog.example.com/42"));
    }
}
