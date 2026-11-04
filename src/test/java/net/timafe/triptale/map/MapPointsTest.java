package net.timafe.triptale.map;

import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.map.PhotoLocations.Photo;
import net.timafe.triptale.util.Coordinates.LatLon;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    private static Photo photo(double lat, Integer hour) {
        return new Photo(new LatLon(lat, lat), hour == null ? null : DAY.atTime(hour, 0));
    }

    @Test
    void photosAloneAreEnoughToPlot() {
        MapPoints points = MapPoints.of(List.of(), Map.of(DAY, List.of(photo(1.0, 9))), false);

        assertFalse(points.isEmpty());
        assertEquals(0, points.dayPinCount());
        assertEquals(1, points.photosFound());
        assertEquals(List.of(), points.route());
    }

    @Test
    void shownPhotosKeepTheFoundCount() {
        List<Photo> photos = IntStream.range(0, 10).mapToObj(i -> photo(i, i)).toList();

        MapPoints points = MapPoints.of(List.of(), Map.of(DAY, photos), false)
                .withPhotosShown(List.of(new LatLon(1, 1)));

        assertEquals(1, points.photos().size());
        assertEquals(10, points.photosFound());
    }

    @Test
    void allDrawsPhotosFirstAndTheStopLast() {
        MapPoints points = MapPoints.of(List.of(entry(0, 1.0, 1.0, 2.0, 2.0)),
                Map.of(DAY, List.of(photo(3.0, 9))), false);

        assertEquals(List.of(new LatLon(3.0, 3.0), new LatLon(1.0, 1.0), new LatLon(2.0, 2.0)), points.all());
    }

    @Test
    void routeRunsStartPhotosStopPerDay_andSkipsUntimedPhotos() {
        MapPoints points = MapPoints.of(
                List.of(entry(0, 1.0, 1.0, 2.0, 2.0), entry(1, 5.0, 5.0, 6.0, 6.0)),
                Map.of(DAY, List.of(photo(3.0, 9), photo(4.0, null)), DAY.plusDays(1), List.of(photo(7.0, 9))),
                true);

        assertEquals(List.of(new LatLon(1, 1), new LatLon(3, 3), new LatLon(2, 2),
                new LatLon(5, 5), new LatLon(7, 7), new LatLon(6, 6)), points.route());
        // the untimed photo is still a pin; only the last day's stop is
        assertEquals(3, points.photos().size());
        assertEquals(new LatLon(6, 6), points.stop());
    }

    @Test
    void routeAlsoFramesTheIntermediateStops() {
        MapPoints points = MapPoints.of(List.of(entry(0, 1.0, 1.0, 9.0, 9.0), entry(1, 5.0, 5.0, null, null)),
                Map.of(), true);

        assertTrue(points.all().contains(new LatLon(9, 9)));
        assertFalse(MapPoints.of(List.of(entry(0, 1.0, 1.0, 9.0, 9.0), entry(1, 5.0, 5.0, null, null)))
                .all().contains(new LatLon(9, 9)));
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
