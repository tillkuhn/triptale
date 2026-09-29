package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinatesTest {

    @Test
    void formatsDegreesAndWholePaddedMinutes() {
        assertEquals("51°29′N 0°01′W", Coordinates.toDdm(51.488, -0.013));
    }

    @Test
    void formatsSouthAndEastHemispheres() {
        assertEquals("43°30′S 16°26′E", Coordinates.toDdm(-43.503, 16.430));
    }

    @Test
    void carriesRoundedMinutesIntoNextDegree() {
        // 59.99 minutes rounds to 60, which must carry into the next whole degree.
        assertEquals("1°00′N 0°00′E", Coordinates.toDdm(0.9999, 0.0));
    }

    @Test
    void treatsZeroAsPositiveHemisphere() {
        assertEquals("0°00′N 0°00′E", Coordinates.toDdm(0.0, 0.0));
    }

    @Test
    void mapsUrlIsPinWithoutStop() {
        assertEquals("https://www.google.com/maps?q=51.4,8.3",
                Coordinates.googleMapsUrl(new Coordinates.LatLon(51.4, 8.3), null));
    }

    @Test
    void mapsUrlIsDirectionsWithStop() {
        assertEquals("https://www.google.com/maps/dir/?api=1&origin=51.4,8.3&destination=51.5,7.9",
                Coordinates.googleMapsUrl(new Coordinates.LatLon(51.4, 8.3), new Coordinates.LatLon(51.5, 7.9)));
    }

    @Test
    void parsesFirstAndLastOfSeveralGpxTrkpts() {
        String gpx = "<trkpt lat=\"51.1\" lon=\"7.1\"></trkpt><trkpt lat=\"51.2\" lon=\"7.2\"></trkpt>"
                + "<trkpt lat=\"51.3\" lon=\"7.3\"></trkpt>";
        assertEquals(Optional.of(new Coordinates.LatLon(51.1, 7.1)), Coordinates.tryParse(gpx));
        assertEquals(Optional.of(new Coordinates.LatLon(51.3, 7.3)), Coordinates.tryParseLast(gpx));
    }

    @Test
    void parsesDirectionsUrlStopsNotViewCentre() {
        String url = "https://www.google.com/maps/dir/51.478536,8.336688/51.5123,+7.912/@51.49,8.1,11z/data=!3m1!4b1";
        assertEquals(Optional.of(new Coordinates.LatLon(51.478536, 8.336688)), Coordinates.tryParse(url));
        assertEquals(Optional.of(new Coordinates.LatLon(51.5123, 7.912)), Coordinates.tryParseLast(url));
    }

    @Test
    void parsesFirstAndLastOfSeveralDirectionsStops() {
        String url = "https://www.google.com/maps/dir/51.1,7.1/51.2,7.2/51.3,7.3/";
        assertEquals(Optional.of(new Coordinates.LatLon(51.1, 7.1)), Coordinates.tryParse(url));
        assertEquals(Optional.of(new Coordinates.LatLon(51.3, 7.3)), Coordinates.tryParseLast(url));
    }

    @Test
    void rejectsDirectionsStopGivenAsPlaceName() {
        String url = "https://www.google.com/maps/dir/Werl/51.5123,7.912/@51.49,8.1,11z";
        assertTrue(Coordinates.tryParse(url).isEmpty());
        assertEquals(Optional.of(new Coordinates.LatLon(51.5123, 7.912)), Coordinates.tryParseLast(url));
    }

    @Test
    void roundTripsOwnDirectionsUrl() {
        String url = Coordinates.googleMapsUrl(new Coordinates.LatLon(51.4, 8.3), new Coordinates.LatLon(51.5, 7.9));
        assertEquals(Optional.of(new Coordinates.LatLon(51.4, 8.3)), Coordinates.tryParse(url));
        assertEquals(Optional.of(new Coordinates.LatLon(51.5, 7.9)), Coordinates.tryParseLast(url));
    }

    @Test
    void lastEqualsFirstForSinglePointFormats() {
        String url = "https://maps.google.com/maps?q=48.8566,2.3522&z=10";
        assertEquals(Coordinates.tryParse(url), Coordinates.tryParseLast(url));
    }

    @Test
    void parsesGpxTrkptAttributes() {
        String gpx = "<trkpt lat=\"51.553443\" lon=\"7.915507\"><ele>94.848927</ele></trkpt>";
        Optional<Coordinates.LatLon> result = Coordinates.tryParse(gpx);
        assertTrue(result.isPresent());
        assertEquals(51.553443, result.get().lat());
        assertEquals(7.915507, result.get().lon());
    }

    @Test
    void parsesGoogleMapsAtCoordinatesWithZoom() {
        String url = "https://www.google.com/maps/place/Split+Port/@43.5028717,16.430357,15z/data";
        Optional<Coordinates.LatLon> result = Coordinates.tryParse(url);
        assertTrue(result.isPresent());
        assertEquals(43.5028717, result.get().lat());
        assertEquals(16.430357, result.get().lon());
    }

    @Test
    void parsesGoogleMapsAtCoordinatesWithoutZoom() {
        Optional<Coordinates.LatLon> result = Coordinates.tryParse("https://maps.google.com/@48.8566,2.3522");
        assertTrue(result.isPresent());
        assertEquals(48.8566, result.get().lat());
        assertEquals(2.3522, result.get().lon());
    }

    @Test
    void parsesGoogleMapsQueryParam() {
        Optional<Coordinates.LatLon> result = Coordinates.tryParse("https://maps.google.com/maps?q=48.8566,2.3522&z=10");
        assertTrue(result.isPresent());
        assertEquals(48.8566, result.get().lat());
        assertEquals(2.3522, result.get().lon());
    }

    @Test
    void parsesGeoJsonArrayAsLonThenLat() {
        Optional<Coordinates.LatLon> result = Coordinates.tryParse("[125.6, 10.1]");
        assertTrue(result.isPresent());
        assertEquals(10.1, result.get().lat());
        assertEquals(125.6, result.get().lon());
    }

    @Test
    void returnsEmptyForUnrecognizedText() {
        assertTrue(Coordinates.tryParse("just some notes, no coordinates here").isEmpty());
    }

    @Test
    void returnsEmptyForBlankInput() {
        assertTrue(Coordinates.tryParse("   ").isEmpty());
        assertTrue(Coordinates.tryParse(null).isEmpty());
    }

    @Test
    void rejectsOutOfRangeGeoJsonCoordinates() {
        assertTrue(Coordinates.tryParse("[200.0, 10.1]").isEmpty());
    }
}
