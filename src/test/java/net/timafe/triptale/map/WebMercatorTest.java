package net.timafe.triptale.map;

import net.timafe.triptale.util.Coordinates.LatLon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebMercatorTest {

    private static final double DELTA = 0.5;

    @Test
    void pixelX_isLinearInLongitude() {
        // At zoom 0 the whole world is one 256px tile; -180 is the left edge, 180 the right edge.
        assertEquals(0.0, WebMercator.pixelX(-180.0, 0), DELTA);
        assertEquals(256.0, WebMercator.pixelX(180.0, 0), DELTA);
        assertEquals(128.0, WebMercator.pixelX(0.0, 0), DELTA);
    }

    @Test
    void pixelY_theEquatorIsAtHalfTheWorldHeight() {
        assertEquals(128.0, WebMercator.pixelY(0.0, 0), DELTA);
        // North of the equator is a smaller y (y grows southward)
        assertTrue(WebMercator.pixelY(45.0, 0) < WebMercator.pixelY(0.0, 0));
        assertTrue(WebMercator.pixelY(-45.0, 0) > WebMercator.pixelY(0.0, 0));
    }

    @Test
    void tilesPerSide_doublesEveryZoomLevel() {
        assertEquals(1.0, WebMercator.tilesPerSide(0));
        assertEquals(2.0, WebMercator.tilesPerSide(1));
        assertEquals(1024.0, WebMercator.tilesPerSide(10));
    }

    @Test
    void marginPixels_isAFractionOfTheSmallestSideCappedAtHalf() {
        assertEquals(96, WebMercator.marginPixels(960, 640, 0.15));
        assertEquals(10, WebMercator.marginPixels(100, 640, 0.10));
        assertEquals(49, WebMercator.marginPixels(100, 640, 0.9), "capped just under half the smallest side");
    }

    @Test
    void fitZoom_aSinglePointFitsAtTheHighestZoom() {
        List<LatLon> point = List.of(new LatLon(51.5, 8.0));
        assertEquals(17, WebMercator.fitZoom(point, 960, 640, 0.1, 17));
    }

    @Test
    void fitZoom_aWideSpreadNeedsALowerZoomThanATightOne() {
        List<LatLon> tight = List.of(new LatLon(51.0, 8.0), new LatLon(51.01, 8.01));
        List<LatLon> wide = List.of(new LatLon(40.0, -10.0), new LatLon(60.0, 20.0));

        int tightZoom = WebMercator.fitZoom(tight, 960, 640, 0.1, 17);
        int wideZoom = WebMercator.fitZoom(wide, 960, 640, 0.1, 17);

        assertTrue(wideZoom < tightZoom, "wide=" + wideZoom + " tight=" + tightZoom);
    }

    @Test
    void fitZoom_resultActuallyFitsTheAvailableArea() {
        List<LatLon> points = List.of(new LatLon(51.0, 7.0), new LatLon(51.8, 8.5));
        int width = 800;
        int height = 600;
        double marginFraction = 0.12;
        int z = WebMercator.fitZoom(points, width, height, marginFraction, 17);

        double minLon = 7.0;
        double maxLon = 8.5;
        double minLat = 51.0;
        double maxLat = 51.8;
        double w = WebMercator.pixelX(maxLon, z) - WebMercator.pixelX(minLon, z);
        double h = WebMercator.pixelY(minLat, z) - WebMercator.pixelY(maxLat, z);
        int margin = WebMercator.marginPixels(width, height, marginFraction);
        assertTrue(w <= width - 2.0 * margin + 0.001);
        assertTrue(h <= height - 2.0 * margin + 0.001);
        // One zoom higher should no longer fit (otherwise fitZoom should have picked it)
        if (z < 17) {
            double w2 = WebMercator.pixelX(maxLon, z + 1) - WebMercator.pixelX(minLon, z + 1);
            double h2 = WebMercator.pixelY(minLat, z + 1) - WebMercator.pixelY(maxLat, z + 1);
            assertTrue(w2 > width - 2.0 * margin + 0.001 || h2 > height - 2.0 * margin + 0.001);
        }
    }
}
