package net.timafe.triptale.storage;

import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifReaderTest {

    @TempDir
    Path tempDir;

    private MarkdownStore store;
    private ExifReader reader;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("settings").toString());
        SettingsStore settingsStore = new SettingsStore(props);
        AppSettings settings = new AppSettings();
        settings.setDataDir(tempDir.resolve("data").toString());
        settingsStore.save(settings);
        store = new MarkdownStore(settingsStore);
        reader = new ExifReader(store);
    }

    private static Path fixture(String name) {
        try {
            return Paths.get(ExifReaderTest.class.getResource("/exif/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Copies a classpath fixture into the data dir, so it's eligible for the on-disk cache (todo 80). */
    private Path copyIntoDataDir(String fixtureName, String fileName) throws IOException {
        Path target = store.dataDir().resolve(fileName);
        Files.copy(fixture(fixtureName), target, StandardCopyOption.REPLACE_EXISTING);
        return target;
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

    // --- todo 80: on-disk cache under <data-dir>/.triptale/cache/exif/ ---

    @Test
    void writesCacheEntryMirroringRelativePathUnderDataDir() throws IOException {
        Path image = copyIntoDataDir("with_exif.jpg", "with_exif.jpg");

        reader.read(image);

        Path cacheFile = store.dataDir().resolve(".triptale/cache/exif").resolve("with_exif.jpg.properties");
        assertTrue(Files.isRegularFile(cacheFile), "expected a cache entry at " + cacheFile);
    }

    @Test
    void cacheHitReturnsCachedDataEvenIfFileContentChangesWithSameSize() throws IOException {
        Path image = copyIntoDataDir("with_exif.jpg", "with_exif.jpg");

        ExifInfo first = reader.read(image);
        assertTrue(first.hasLocation());

        // Overwrite the file in place with garbage of the exact same size — a live re-read would
        // fail to parse any EXIF/GPS data, so if the second read still returns the original GPS
        // coordinates, that proves it came from the cache rather than a fresh parse.
        long size = Files.size(image);
        Files.write(image, new byte[(int) size]);

        ExifInfo second = reader.read(image);
        assertEquals(first, second);
        assertTrue(second.hasLocation());
    }

    @Test
    void cacheMissWhenFileSizeChanges() throws IOException {
        Path image = copyIntoDataDir("with_exif.jpg", "photo.jpg");

        ExifInfo first = reader.read(image);
        assertTrue(first.hasLocation());

        // Replace with a different fixture (different EXIF, near-certainly a different byte size).
        Path replacement = fixture("with_iso.jpg");
        assertNotEquals(Files.size(image), Files.size(replacement));
        Files.copy(replacement, image, StandardCopyOption.REPLACE_EXISTING);

        ExifInfo second = reader.read(image);
        assertEquals("ISO 200", second.iso());
        assertFalse(first.equals(second));
    }

    @Test
    void skipsCacheForFilesOutsideTheDataDir() {
        // Classpath fixtures live outside the configured data dir.
        Path image = fixture("with_exif.jpg");

        ExifInfo info = reader.read(image);

        assertTrue(info.hasLocation());
        Path wouldBeCacheFile = store.dataDir().resolve(".triptale/cache/exif")
                .resolve(image.toAbsolutePath().toString().replaceFirst("^/", "") + ".properties");
        assertFalse(Files.exists(wouldBeCacheFile));
    }

    @Test
    void skipsCacheGracefullyWhenDataDirIsNotConfigured() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("unconfigured-settings").toString());
        ExifReader unconfigured = new ExifReader(new MarkdownStore(new SettingsStore(props)));

        ExifInfo info = unconfigured.read(fixture("with_exif.jpg"));

        assertTrue(info.hasLocation());
    }
}
