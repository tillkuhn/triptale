package net.timafe.triptale.impressions;

import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.ImpressionsResolver;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.PathPatternResolver;
import net.timafe.triptale.storage.SettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpressionsServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 4);

    @TempDir
    Path tempDir;

    private SettingsStore settingsStore;
    private AttachmentsDir attachmentsDir;
    private ImpressionsService service;
    private final Trip trip = new Trip(2026, "alps", "Alps", DAY, null, "");

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("settings").toString());
        settingsStore = new SettingsStore(props);
        AppSettings settings = new AppSettings();
        settings.setDataDir(tempDir.resolve("data").toString());
        settingsStore.save(settings);
        attachmentsDir = new AttachmentsDir(new MarkdownStore(settingsStore));
        service = new ImpressionsService(new ImpressionsResolver(new PathPatternResolver()), attachmentsDir, settingsStore);
    }

    private void update(java.util.function.Consumer<AppSettings> change) {
        AppSettings s = settingsStore.load();
        change.accept(s);
        settingsStore.save(s);
    }

    @Test
    void attachmentsSourceListsDayFolderWithBaseFilterSorted() throws IOException {
        Path dir = attachmentsDir.ensureDayDir(trip.ref(), DAY);
        Files.createFile(dir.resolve("b.jpg"));
        Files.createFile(dir.resolve("a.PNG"));
        Files.createFile(dir.resolve("track.gpx"));

        List<Path> images = service.images(ImpressionSource.TRIP_ATTACHMENTS, trip, DAY);

        assertEquals(List.of(dir.resolve("a.PNG"), dir.resolve("b.jpg")), images);
        assertEquals(Optional.of(dir), service.directory(ImpressionSource.TRIP_ATTACHMENTS, trip, DAY));
    }

    @Test
    void attachmentsSourceIsEmptyWithoutDayFolder() {
        assertTrue(service.images(ImpressionSource.TRIP_ATTACHMENTS, trip, DAY).isEmpty());
        assertTrue(service.directory(ImpressionSource.TRIP_ATTACHMENTS, trip, DAY).isEmpty());
    }

    @Test
    void photoLibrarySourceCombinesPatternAndBaseFilter() throws IOException {
        Path lib = Files.createDirectories(tempDir.resolve("pics"));
        Files.createFile(lib.resolve("20260604_1.jpg"));
        Files.createFile(lib.resolve("20260604_2.heic"));
        Files.createFile(lib.resolve("20260605_1.jpg"));
        update(s -> s.setImpressionsFilePattern(lib + "/${DATE}*"));

        assertTrue(service.photoLibraryConfigured());
        assertEquals(List.of(lib.resolve("20260604_1.jpg")), service.images(ImpressionSource.PHOTO_LIBRARY, trip, DAY));
        assertEquals(Optional.of(lib), service.directory(ImpressionSource.PHOTO_LIBRARY, trip, DAY));
    }

    @Test
    void photoLibrarySourceIsEmptyWhenNotConfigured() {
        assertFalse(service.photoLibraryConfigured());
        assertTrue(service.images(ImpressionSource.PHOTO_LIBRARY, trip, DAY).isEmpty());
    }

    @Test
    void folderSourceIgnoresTheDate() throws IOException {
        Path folder = Files.createDirectories(tempDir.resolve("camera"));
        Files.createFile(folder.resolve("IMG_0001.JPG"));
        Files.createFile(folder.resolve(".DS_Store"));

        assertEquals(List.of(folder.resolve("IMG_0001.JPG")),
                service.images(new ImpressionSource.Folder(folder), trip, DAY));
    }

    @Test
    void faveFilterComesFromSettings() {
        assertTrue(service.faveFilter().isEmpty());
        update(s -> s.setImpressionsFaveFilter("*+.*"));
        assertTrue(service.faveFilter().matches(Path.of("c+.jpg")));
        assertFalse(service.faveFilter().matches(Path.of("c.jpg")));
    }

    @Test
    void importCopiesIntoDayFolderOverwritingSameName() throws IOException {
        Path folder = Files.createDirectories(tempDir.resolve("camera"));
        Path img = Files.writeString(folder.resolve("IMG_0001.jpg"), "new");
        Path dir = attachmentsDir.ensureDayDir(trip.ref(), DAY);
        Files.writeString(dir.resolve("IMG_0001.jpg"), "old");

        List<Path> copies = service.importToAttachments(trip, DAY, List.of(img));

        assertEquals(List.of(dir.resolve("IMG_0001.jpg")), copies);
        assertEquals("new", Files.readString(dir.resolve("IMG_0001.jpg")));
        assertTrue(Files.exists(img), "import copies, it doesn't move");
    }
}
