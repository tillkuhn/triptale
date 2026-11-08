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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpxCandidatesTest {

    private static final LocalDate DAY = LocalDate.of(2024, 9, 6);

    @TempDir
    Path tempDir;

    private SettingsStore settingsStore;
    private AttachmentsDir attachmentsDir;
    private GpxCandidates candidates;
    private final Trip trip = new Trip(2024, "lahn", "Lahn", DAY, null, "");

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("settings").toString());
        settingsStore = new SettingsStore(props);
        AppSettings settings = new AppSettings();
        settings.setDataDir(tempDir.resolve("data").toString());
        settingsStore.save(settings);
        attachmentsDir = new AttachmentsDir(new MarkdownStore(settingsStore));
        candidates = new GpxCandidates(new ImpressionsResolver(new PathPatternResolver()), settingsStore, attachmentsDir);
    }

    private void pattern(String p) {
        AppSettings s = settingsStore.load();
        s.setGpxFilePattern(p);
        settingsStore.save(s);
    }

    @Test
    void find_matchesByDatePattern() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("tours"));
        Files.writeString(dir.resolve("2024-09-06_111_a.gpx"), "x");
        Files.writeString(dir.resolve("2024-09-06_222_b.gpx"), "x");
        Files.writeString(dir.resolve("2024-09-07_333_c.gpx"), "x");
        pattern(dir + "/${ENTRY_YEAR}-${ENTRY_MONTH}-${ENTRY_DAY}_*.gpx");

        List<Path> found = candidates.find(trip, DAY);

        assertEquals(List.of("2024-09-06_111_a.gpx", "2024-09-06_222_b.gpx"),
                found.stream().map(p -> p.getFileName().toString()).toList());
    }

    @Test
    void find_resolvesUserVariable() throws IOException {
        String user = System.getProperty("user.name");
        Path dir = Files.createDirectories(tempDir.resolve(user));
        Files.writeString(dir.resolve("2024-09-06_1_a.gpx"), "x");
        pattern(tempDir + "/${USER}/${ENTRY_YEAR}-${ENTRY_MONTH}-${ENTRY_DAY}_*.gpx");

        assertEquals(1, candidates.find(trip, DAY).size());
    }

    @Test
    void find_blankPatternIsEmpty() {
        assertTrue(candidates.find(trip, DAY).isEmpty());
    }

    @Test
    void hasImported_detectsGpxInBackpack() throws IOException {
        assertFalse(candidates.hasImported(trip, DAY));
        Path gpx = tempDir.resolve("t.GPX");
        Files.writeString(gpx, "x");
        attachmentsDir.addFiles(trip.ref(), DAY, List.of(gpx));
        assertTrue(candidates.hasImported(trip, DAY));
    }
}
