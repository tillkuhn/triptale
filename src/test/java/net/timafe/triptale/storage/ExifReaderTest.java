package net.timafe.triptale.storage;

import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

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

        assertEquals("4×4", info.dimensions());
    }

    @Test
    void readsDimensionsEvenWithoutExif() {
        ExifInfo info = reader.read(fixture("no_exif.jpg"));

        assertEquals("4×4", info.dimensions());
    }

    @Test
    void dedupesModelThatAlreadyContainsMake() {
        ExifInfo info = reader.read(fixture("dedup_model.jpg"));

        assertEquals("Canon EOS R5", info.cameraModel());
        assertFalse(info.hasLocation());
        assertNull(info.mapsUrl());
    }

    @Test
    void readsIsoWhenPresent() {
        ExifInfo info = reader.read(fixture("with_iso.jpg"));

        assertEquals("ISO 200", info.iso());
        assertTrue(info.hasCameraData());
    }

    @Test
    void isoIsNullWhenNotPresent() {
        ExifInfo info = reader.read(fixture("with_exif.jpg"));

        assertNull(info.iso());
    }

    @Test
    void formatsExposureTimeUnderOneSecondAsFraction() {
        assertEquals("1/250 sec", ExifReader.formatExposureTime(1.0 / 250));
    }

    @Test
    void formatsRawUnsimplifiedExposureTimeAsRoundedFraction() {
        // Some cameras (e.g. certain Samsung models) report exposure as an un-simplified
        // fraction such as 3030303/100000000 sec (~1/33 sec).
        assertEquals("1/33 sec", ExifReader.formatExposureTime(3030303.0 / 100000000.0));
    }

    @Test
    void formatsExposureTimeOfOneSecondOrLongerAsDecimalSeconds() {
        assertEquals("1 sec", ExifReader.formatExposureTime(1.0));
        assertEquals("2.5 sec", ExifReader.formatExposureTime(2.5));
    }

    @Test
    void returnsEmptyForImageWithoutExif() {
        ExifInfo info = reader.read(fixture("no_exif.jpg"));

        assertNull(info.cameraModel());
        assertNull(info.aperture());
        assertNull(info.exposureTime());
        assertNull(info.iso());
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

    @Test
    void parsesCaptureTimeFromDateTimeOriginal() {
        assertEquals(LocalDateTime.of(2026, 8, 19, 10, 37, 10),
                ExifReader.captureTime(metadataWithDateTimeOriginal("2026:08:19 10:37:10")));
    }

    @Test
    void captureTimeIsNullWhenMissingOrBlank() {
        assertNull(ExifReader.captureTime(new Metadata()));
        assertNull(ExifReader.captureTime(metadataWithDateTimeOriginal("    :  :     ")));
        assertNull(ExifReader.captureTime(metadataWithDateTimeOriginal("garbage")));
        assertNull(reader.read(fixture("no_exif.jpg")).captureTime());
    }

    private static Metadata metadataWithDateTimeOriginal(String value) {
        ExifSubIFDDirectory dir = new ExifSubIFDDirectory();
        dir.setString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, value);
        Metadata metadata = new Metadata();
        metadata.addDirectory(dir);
        return metadata;
    }
}
