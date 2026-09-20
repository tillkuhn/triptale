package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinatesTest {

    @Test
    void formatsDdmWithHemisphereLetters() {
        assertEquals("51° 29.3' N 0° 0.8' W", Coordinates.toDdm(51.488, -0.013));
    }

    @Test
    void formatsSouthAndEastHemispheres() {
        assertEquals("43° 30.2' S 16° 25.8' E", Coordinates.toDdm(-43.503, 16.430));
    }

    @Test
    void carriesRoundedMinutesIntoNextDegree() {
        // 59.99 minutes rounds to 60.0, which must carry into the next whole degree.
        assertEquals("1° 0.0' N 0° 0.0' E", Coordinates.toDdm(0.9999833, 0.0));
    }

    @Test
    void treatsZeroAsPositiveHemisphere() {
        assertEquals("0° 0.0' N 0° 0.0' E", Coordinates.toDdm(0.0, 0.0));
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
