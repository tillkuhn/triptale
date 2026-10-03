package net.timafe.triptale.attachments;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.AppSettings.AttachmentSync;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.TripRef;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentsDirTest {

    @TempDir
    Path tempDir;

    private Path dataDir;
    private AttachmentsDir attachmentsDir;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("settings").toString());
        SettingsStore settingsStore = new SettingsStore(props);
        dataDir = tempDir.resolve("data");
        AppSettings settings = new AppSettings();
        settings.setDataDir(dataDir.toString());
        settingsStore.save(settings);
        attachmentsDir = new AttachmentsDir(new MarkdownStore(settingsStore));
    }

    @Test
    void dayDir_mirrorsEntryFileNameWithoutCreatingIt() {
        Path dir = attachmentsDir.dayDir(new TripRef(2026, "altenbeken-ruhr"), LocalDate.of(2026, 9, 26));
        assertEquals(dataDir.resolve("attachments/2026/altenbeken-ruhr/2026-09-26-Saturday"), dir);
        assertFalse(Files.exists(dir));
    }

    @Test
    void ensureDayDir_createsOnDemand() {
        Path dir = attachmentsDir.ensureDayDir(new TripRef(2026, "trip"), LocalDate.of(2026, 9, 26));
        assertTrue(Files.isDirectory(dir));
    }

    @Test
    void addFiles_copiesIntoDayDirAndOverwritesSameName() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("downloads"));
        Path gpx = Files.writeString(source.resolve("track.gpx"), "v1");
        Path photo = Files.writeString(source.resolve("photo.jpg"), "jpg");
        TripRef ref = new TripRef(2026, "trip");
        LocalDate date = LocalDate.of(2026, 9, 26);

        List<Path> copies = attachmentsDir.addFiles(ref, date, List.of(gpx, photo));

        Path dayDir = attachmentsDir.dayDir(ref, date);
        assertEquals(List.of(dayDir.resolve("track.gpx"), dayDir.resolve("photo.jpg")), copies);
        assertTrue(Files.exists(gpx), "source stays where it was");

        Files.writeString(gpx, "v2");
        attachmentsDir.addFiles(ref, date, List.of(gpx));
        assertEquals("v2", Files.readString(dayDir.resolve("track.gpx")));
    }

    @Test
    void ensureGitignore_writesPerModeAndReportsChanges() throws Exception {
        Path gitignore = dataDir.resolve("attachments/.gitignore");

        assertTrue(attachmentsDir.ensureGitignore(AttachmentSync.OFF));
        assertEquals(AttachmentsDir.GITIGNORE_LOCAL, Files.readString(gitignore));
        assertFalse(attachmentsDir.ensureGitignore(AttachmentSync.CLOUD), "off and cloud share the content");

        assertTrue(attachmentsDir.ensureGitignore(AttachmentSync.GIT));
        assertEquals(AttachmentsDir.GITIGNORE_GIT, Files.readString(gitignore));
        assertFalse(attachmentsDir.ensureGitignore(AttachmentSync.GIT));
    }
}
