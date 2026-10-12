package net.timafe.triptale.map;

import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.util.Coordinates.LatLon;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPointsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 4);

    private static DiaryEntry entry(int day, Double startLat, Double startLon, Double stopLat, Double stopLon) {
        return DiaryEntry.builder(DAY.plusDays(day))
                .startLat(startLat).startLon(startLon).stopLat(stopLat).stopLon(stopLon)
                .build();
    }

    @Test
    void startsOfEveryEntryAndStopOfTheLastOnly() {
        MapPoints points = MapPoints.of(List.of(
                entry(0, 1.0, 2.0, 3.0, 4.0),
                entry(1, null, null, null, null),
                entry(2, 5.0, 6.0, 7.0, 8.0)));

        assertEquals(List.of(new LatLon(1.0, 2.0), new LatLon(5.0, 6.0)), points.starts());
        assertEquals(new LatLon(7.0, 8.0), points.stop());
        assertEquals(3, points.dayPinCount());
    }

    @Test
    void noStopWhenTheLastEntryHasNone() {
        MapPoints points = MapPoints.of(List.of(entry(0, 1.0, 2.0, 3.0, 4.0), entry(1, 5.0, 6.0, null, null)));

        assertNull(points.stop());
    }

    @Test
    void halfACoordinateIsNoPoint() {
        MapPoints points = MapPoints.of(List.of(entry(0, 1.0, null, 3.0, null)));

        assertTrue(points.isEmpty());
    }

    @Test
    void zeroIsARealCoordinate() {
        MapPoints points = MapPoints.of(List.of(entry(0, 0.0, 0.0, null, null)));

        assertEquals(List.of(new LatLon(0.0, 0.0)), points.starts());
    }

    @Test
    void photosAloneAreEnoughToPlot() {
        MapPoints points = MapPoints.of(List.of()).withPhotos(List.of(new LatLon(1.0, 2.0)), 10);

        assertFalse(points.isEmpty());
        assertEquals(0, points.dayPinCount());
        assertEquals(1, points.photosFound());
    }

    @Test
    void photosAreThinnedButTheFoundCountIsKept() {
        List<LatLon> photos = IntStream.range(0, 10).mapToObj(i -> new LatLon(i, i)).toList();

        MapPoints points = MapPoints.of(List.of()).withPhotos(photos, 4);

        assertEquals(4, points.photos().size());
        assertEquals(10, points.photosFound());
    }

    @Test
    void allDrawsPhotosFirstAndTheStopLast() {
        MapPoints points = MapPoints.of(List.of(entry(0, 1.0, 1.0, 2.0, 2.0)))
                .withPhotos(List.of(new LatLon(3.0, 3.0)), 10);

        assertEquals(List.of(new LatLon(3.0, 3.0), new LatLon(1.0, 1.0), new LatLon(2.0, 2.0)), points.all());
    }

    @Test
    void sampleEvenlySpreadsOverTheWholeList() {
        List<Integer> items = IntStream.range(0, 10).boxed().toList();

        assertEquals(List.of(0, 2, 5, 7), MapPoints.sampleEvenly(items, 4));
        assertEquals(items, MapPoints.sampleEvenly(items, 10));
        assertEquals(items, MapPoints.sampleEvenly(items, 50));
        assertEquals(List.of(), MapPoints.sampleEvenly(items, 0));
        assertEquals(List.of(), MapPoints.sampleEvenly(items, -3));
    }
}
