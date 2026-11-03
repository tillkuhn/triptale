package net.timafe.triptale.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MapFormatTest {

    @Test
    void fullSize_capsTheLongEdgeAt960() {
        assertArrayEquals(new int[] {960, 640}, MapFormat.LANDSCAPE.fullSize());
        assertArrayEquals(new int[] {960, 960}, MapFormat.SQUARE.fullSize());
        assertArrayEquals(new int[] {640, 960}, MapFormat.PORTRAIT.fullSize());
    }

    @Test
    void fit_usesTheFullSizeWhenThereIsRoom() {
        assertArrayEquals(new int[] {640, 960}, MapFormat.PORTRAIT.fit(2000, 1200));
    }

    @Test
    void fit_shrinksToTheTighterDimensionKeepingTheRatio() {
        // 14" MacBook: ~1430×780 usable for the map
        assertArrayEquals(new int[] {960, 640}, MapFormat.LANDSCAPE.fit(1430, 780));
        assertArrayEquals(new int[] {780, 780}, MapFormat.SQUARE.fit(1430, 780));
        assertArrayEquals(new int[] {520, 780}, MapFormat.PORTRAIT.fit(1430, 780));
    }

    @Test
    void fit_neverGoesBelowHalfSize() {
        assertArrayEquals(new int[] {480, 320}, MapFormat.LANDSCAPE.fit(100, 100));
    }
}
