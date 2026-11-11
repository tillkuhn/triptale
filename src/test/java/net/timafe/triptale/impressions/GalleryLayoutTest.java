package net.timafe.triptale.impressions;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GalleryLayoutTest {

    private static final double L = 1.5;
    private static final double P = 2.0 / 3;

    private static List<List<Integer>> indices(List<Double> aspects, int cols) {
        return GalleryLayout.plan(aspects, cols).stream()
                .map(r -> r.cells().stream().map(GalleryLayout.Cell::index).toList())
                .toList();
    }

    @Test
    void pairsSameOrientation() {
        assertEquals(List.of(List.of(0, 2), List.of(1, 3)), indices(List.of(L, P, L, P), 2));
    }

    @Test
    void oddLandscapeGoesLastAlone() {
        assertEquals(List.of(List.of(0, 1), List.of(2)), indices(List.of(L, L, L), 2));
    }

    @Test
    void oddLandscapeAndOddPortraitShareAMixedRow() {
        assertEquals(List.of(List.of(1, 0)), indices(List.of(L, P), 2));
    }

    @Test
    void portraitRowsPrecedeTheTrailingLandscape() {
        // 3 portraits + 1 landscape, 2 columns: P pair, then leftover P and L mixed
        assertEquals(List.of(List.of(1, 2), List.of(3, 0)), indices(List.of(L, P, P, P), 2));
    }

    @Test
    void loneTrailingPortraitBorrowsALandscape() {
        // 4 landscapes + 1 portrait: the portrait must not end up alone and full width
        assertEquals(List.of(List.of(0, 1), List.of(4, 2), List.of(3)), indices(List.of(L, L, L, L, P), 2));
    }

    @Test
    void unknownAspectCountsAsLandscape() {
        assertEquals(List.of(List.of(0, 1)), indices(Arrays.asList(null, L), 2));
    }

    @Test
    void rowHeightFollowsMeanAspect() {
        GalleryLayout.Row single = GalleryLayout.plan(List.of(2.0), 2).getFirst();
        assertEquals(0.5, single.heightRatio(), 1e-9);
        GalleryLayout.Row pair = GalleryLayout.plan(List.of(1.5, 1.5), 2).getFirst();
        assertEquals(1.0 / 3, pair.heightRatio(), 1e-9);
    }

    @Test
    void emptyInputGivesNoRows() {
        assertEquals(List.of(), GalleryLayout.plan(List.of(), 2));
    }
}
