package net.timafe.triptale.storage;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifReaderTest {

    private final ExifReader reader = new ExifReader();

    private static Path fixture(String name) {
        try {
            return Paths.get(ExifReaderTest.class.getResource("/exif/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void readsCameraApertureExposureAndGps() {
        ExifInfo info = reader.read(fixture("with_exif.jpg"));

        assertEquals("Apple iPhone 14 Pro", info.cameraModel());
        assertEquals("f/2.8", info.aperture());
        assertEquals("1/250 sec", info.exposureTime());
        assertTrue(info.hasCameraData());

        assertTrue(info.hasLocation());
        assertEquals(48.8557, info.latitude(), 0.001);
        assertEquals(2.3520, info.longitude(), 0.001);
        assertEquals("https://www.google.com/maps?q=48.85566111111111,2.352027777777778", info.mapsUrl());
    }

    @Test
    void dedupesModelThatAlreadyContainsMake() {
        ExifInfo info = reader.read(fixture("dedup_model.jpg"));

        assertEquals("Canon EOS R5", info.cameraModel());
        assertFalse(info.hasLocation());
        assertNull(info.mapsUrl());
    }

    @Test
    void returnsEmptyForImageWithoutExif() {
        ExifInfo info = reader.read(fixture("no_exif.jpg"));

        assertNull(info.cameraModel());
        assertNull(info.aperture());
        assertNull(info.exposureTime());
        assertFalse(info.hasCameraData());
        assertFalse(info.hasLocation());
        assertNull(info.mapsUrl());
    }

    @Test
    void returnsEmptyForMissingFile() {
        ExifInfo info = reader.read(Path.of("/no/such/file.jpg"));

        assertEquals(ExifInfo.empty(), info);
    }

    @Test
    void returnsEmptyForNullPath() {
        assertEquals(ExifInfo.empty(), reader.read(null));
    }
}
