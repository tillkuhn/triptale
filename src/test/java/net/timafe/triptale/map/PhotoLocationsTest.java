package net.timafe.triptale.map;

import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.ExifReader;
import net.timafe.triptale.storage.ImpressionsResolver;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.PathPatternResolver;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.util.Coordinates.LatLon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoLocationsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 4);

    @TempDir
    Path tempDir;

    private SettingsStore settingsStore;
    private AttachmentsDir attachmentsDir;
    private PhotoLocations photoLocations;
    private final Trip trip = new Trip(2026, "alps", "Alps", DAY, null, "");

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("settings").toString());
        settingsStore = new SettingsStore(props);
        AppSettings settings = new AppSettings();
        settings.setDataDir(tempDir.resolve("data").toString());
        settings.setImpressionsFaveFilter("*_fave.jpg");
        settingsStore.save(settings);
        attachmentsDir = new AttachmentsDir(new MarkdownStore(settingsStore));
        ImpressionsService impressions = new ImpressionsService(
                new ImpressionsResolver(new PathPatternResolver()), attachmentsDir, settingsStore);
        photoLocations = new PhotoLocations(impressions, new ExifReader());
    }

    private void update(Consumer<AppSettings> change) {
        AppSettings s = settingsStore.load();
        change.accept(s);
        settingsStore.save(s);
    }

    private static Path fixture(String name) {
        try {
            return Paths.get(PhotoLocationsTest.class.getResource("/exif/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path pack(LocalDate date, String fixture, String name) throws IOException {
        return Files.copy(fixture(fixture), attachmentsDir.ensureDayDir(trip.ref(), date).resolve(name));
    }

    @Test
    void favesOfTheRangeInDateOrder() throws IOException {
        Path day2 = pack(DAY.plusDays(1), "with_exif.jpg", "b_fave.jpg");
        Path day1 = pack(DAY, "with_exif.jpg", "a_fave.jpg");
        pack(DAY, "with_exif.jpg", "other.jpg");
        pack(DAY.plusDays(2), "with_exif.jpg", "outside_fave.jpg");

        assertEquals(List.of(day1, day2), photoLocations.faves(trip, DAY, DAY.plusDays(1)));
    }

    @Test
    void noFaveFilterMeansNoPhotoLocations() throws IOException {
        update(s -> s.setImpressionsFaveFilter(""));
        pack(DAY, "with_exif.jpg", "a_fave.jpg");

        assertFalse(photoLocations.available());
        assertTrue(photoLocations.faves(trip, DAY, DAY).isEmpty());
    }

    @Test
    void locationsAreRoundedAndSkipImagesWithoutGps() throws IOException {
        Path withGps = pack(DAY, "with_exif.jpg", "a_fave.jpg");
        Path withoutGps = pack(DAY, "no_exif.jpg", "b_fave.jpg");

        List<LatLon> locations = photoLocations.locations(List.of(withGps, withoutGps));

        assertEquals(List.of(new LatLon(48.85566, 2.35203)), locations);
    }
}
