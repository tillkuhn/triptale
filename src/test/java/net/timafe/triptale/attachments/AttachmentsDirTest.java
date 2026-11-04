package net.timafe.triptale.attachments;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.AppSettings.AttachmentSync;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.TripRef;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.storage.StorageException;
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
    void tripDir_isOneLevelAboveTheDayDirs() {
        Path dir = attachmentsDir.tripDir(new TripRef(2026, "altenbeken-ruhr"));
        assertEquals(dataDir.resolve("attachments/2026/altenbeken-ruhr"), dir);
    }

    @Test
    void tripMapFile_isAFixedNameInTheDayDir() {
        TripRef ref = new TripRef(2026, "altenbeken-ruhr");
        LocalDate date = LocalDate.of(2026, 9, 26);
        assertEquals(attachmentsDir.dayDir(ref, date).resolve("tripmap.png"), attachmentsDir.tripMapFile(ref, date));
    }

    @Test
    void saveTripMap_createsTheDayDirAndReplacesAnEarlierMap() throws Exception {
        TripRef ref = new TripRef(2026, "trip");
        LocalDate date = LocalDate.of(2026, 9, 26);
        Files.createDirectories(dataDir);
        Path first = Files.writeString(dataDir.resolve("first.png"), "first");
        Path second = Files.writeString(dataDir.resolve("second.png"), "second");
        attachmentsDir.saveTripMap(ref, date, first);
        Path saved = attachmentsDir.saveTripMap(ref, date, second);
        assertEquals(attachmentsDir.tripMapFile(ref, date), saved);
        assertEquals("second", Files.readString(saved));
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
    void dayFiles_emptyWhenTheDayDirIsMissing() {
        assertEquals(List.of(), attachmentsDir.dayFiles(new TripRef(2026, "trip"), LocalDate.of(2026, 9, 26)));
    }

    @Test
    void dayFiles_listsVisibleFilesByNameAndSkipsHiddenAndSubfolders() throws Exception {
        TripRef ref = new TripRef(2026, "trip");
        LocalDate date = LocalDate.of(2026, 9, 26);
        Path dir = attachmentsDir.ensureDayDir(ref, date);
        Files.writeString(dir.resolve("track.gpx"), "gpx");
        Files.writeString(dir.resolve("Beach.JPG"), "jpeg!");
        Files.writeString(dir.resolve(AttachmentsDir.TRIP_MAP_FILE), "png");
        Files.writeString(dir.resolve(".DS_Store"), "x");
        Files.writeString(dir.resolve(".photo.jpg.part"), "x");
        Files.createDirectories(dir.resolve("sub"));

        List<BackpackFile> files = attachmentsDir.dayFiles(ref, date);

        assertEquals(List.of("Beach.JPG", "track.gpx", "tripmap.png"), files.stream().map(BackpackFile::name).toList());
        assertEquals(5, files.getFirst().size());
        assertEquals(FileKind.IMAGE, files.getFirst().kind());
        assertEquals(FileKind.TRACK, files.get(1).kind());
    }

    @Test
    void deleteFile_removesOnlyThatFile() throws Exception {
        TripRef ref = new TripRef(2026, "trip");
        LocalDate date = LocalDate.of(2026, 9, 26);
        Path dir = attachmentsDir.ensureDayDir(ref, date);
        Files.writeString(dir.resolve("a.txt"), "a");
        Files.writeString(dir.resolve("b.txt"), "b");

        attachmentsDir.deleteFile(ref, date, "a.txt");

        assertFalse(Files.exists(dir.resolve("a.txt")));
        assertTrue(Files.exists(dir.resolve("b.txt")));
    }

    @Test
    void deleteFile_refusesPathsOutsideTheDayDirAndMissingFiles() throws Exception {
        TripRef ref = new TripRef(2026, "trip");
        LocalDate date = LocalDate.of(2026, 9, 26);
        Path dir = attachmentsDir.ensureDayDir(ref, date);
        Path outside = Files.writeString(dir.getParent().resolve("outside.txt"), "x");

        assertThrows(StorageException.class, () -> attachmentsDir.deleteFile(ref, date, "../outside.txt"));
        assertThrows(StorageException.class, () -> attachmentsDir.deleteFile(ref, date, "missing.txt"));
        assertTrue(Files.exists(outside));
    }

    @Test
    void ensureManagedFiles_writesPerModeAndReportsChanges() throws Exception {
        Path gitignore = dataDir.resolve("attachments/.gitignore");
        Path gitattributes = dataDir.resolve("attachments/.gitattributes");

        assertEquals(List.of(AttachmentsDir.GITIGNORE_LABEL, AttachmentsDir.GITATTRIBUTES_LABEL),
                attachmentsDir.ensureManagedFiles(AttachmentSync.OFF));
        assertEquals(AttachmentsDir.GITIGNORE_LOCAL, Files.readString(gitignore));
        assertEquals(AttachmentsDir.GITATTRIBUTES, Files.readString(gitattributes));
        assertEquals(List.of(), attachmentsDir.ensureManagedFiles(AttachmentSync.CLOUD),
                "off and cloud share the content");

        assertEquals(List.of(AttachmentsDir.GITIGNORE_LABEL), attachmentsDir.ensureManagedFiles(AttachmentSync.GIT));
        assertEquals(AttachmentsDir.GITIGNORE_GIT, Files.readString(gitignore));
        assertEquals(List.of(), attachmentsDir.ensureManagedFiles(AttachmentSync.GIT));
    }

    @Test
    void ensureManagedFiles_localModeKeepsManagedFilesVisibleToGit() {
        assertTrue(AttachmentsDir.GITIGNORE_LOCAL.contains("!.gitignore"));
        assertTrue(AttachmentsDir.GITIGNORE_LOCAL.contains("!.gitattributes"));
    }

    @Test
    void ensureManagedFiles_restoresEditedGitattributes() throws Exception {
        attachmentsDir.ensureManagedFiles(AttachmentSync.CLOUD);
        Files.writeString(dataDir.resolve("attachments/.gitattributes"), "*.gpx text eol=crlf\n");

        assertEquals(List.of(AttachmentsDir.GITATTRIBUTES_LABEL), attachmentsDir.ensureManagedFiles(AttachmentSync.CLOUD));
    }
}
