package net.timafe.triptale.storage;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

class SettingsStoreTest {

    @TempDir
    Path tempDir;

    private SettingsStore settingsStore;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.toString());
        settingsStore = new SettingsStore(props);
    }

    @Test
    void loadReturnsDefaultsWhenFileMissing() {
        AppSettings settings = settingsStore.load();
        assertEquals("", settings.getDataDir());
        assertEquals("", settings.getGit().getAuthorName());
        assertEquals("", settings.getGit().getAuthorEmail());
        assertEquals("", settings.getImpressionsFilePattern());
        assertEquals(2, settings.getImpressionsGridColumns());
        assertEquals("", settings.getImpressionsFaveFilePattern());
        assertEquals("", settings.getAttachments().getBucketUrl());
        assertEquals("eu-central-1", settings.getAttachments().getRegion());
    }

    @Test
    void saveThenLoadRoundTripsAllFields() {
        AppSettings settings = new AppSettings();
        settings.setDataDir("/some/data/dir");
        AppSettings.Git git = new AppSettings.Git();
        git.setAuthorName("Alice");
        git.setAuthorEmail("alice@example.com");
        settings.setGit(git);
        settings.setImpressionsFilePattern("${HOME}/Pictures/output/${DATE}*.jpg");
        settings.setImpressionsGridColumns(4);
        settings.setImpressionsFaveFilePattern("${HOME}/Pictures/00_Faves/${DATE}*.jpg");
        AppSettings.Attachments attachments = new AppSettings.Attachments();
        attachments.setBucketUrl("s3://triptale-attachments/trips");
        attachments.setRegion("us-east-1");
        attachments.setAccessKeyId("AKIAEXAMPLE");
        attachments.setSecretAccessKey("secret/with+chars");
        settings.setAttachments(attachments);

        settingsStore.save(settings);

        AppSettings loaded = settingsStore.load();
        assertEquals("/some/data/dir", loaded.getDataDir());
        assertEquals("Alice", loaded.getGit().getAuthorName());
        assertEquals("alice@example.com", loaded.getGit().getAuthorEmail());
        assertEquals("${HOME}/Pictures/output/${DATE}*.jpg", loaded.getImpressionsFilePattern());
        assertEquals(4, loaded.getImpressionsGridColumns());
        assertEquals("${HOME}/Pictures/00_Faves/${DATE}*.jpg", loaded.getImpressionsFaveFilePattern());
        assertEquals("s3://triptale-attachments/trips", loaded.getAttachments().getBucketUrl());
        assertEquals("us-east-1", loaded.getAttachments().getRegion());
        assertEquals("AKIAEXAMPLE", loaded.getAttachments().getAccessKeyId());
        assertEquals("secret/with+chars", loaded.getAttachments().getSecretAccessKey());
    }

    @Test
    void loadKeepsDefaultRegionWhenAttachmentsGroupMissing() throws Exception {
        Files.writeString(settingsStore.settingsFile(), "dataDir: /some/data/dir\n");
        assertEquals("eu-central-1", settingsStore.load().getAttachments().getRegion());
    }

    @Test
    void syncModeIsWrittenLowercase() throws Exception {
        AppSettings settings = new AppSettings();
        settings.getAttachments().setSync(AppSettings.AttachmentSync.CLOUD);
        settingsStore.save(settings);

        assertTrue(Files.readString(settingsStore.settingsFile()).contains("sync: cloud"));
        assertEquals(AppSettings.AttachmentSync.CLOUD, settingsStore.load().getAttachments().getSync());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void saveMakesNewFileOwnerOnly() throws Exception {
        settingsStore.save(new AppSettings());
        assertEquals("rw-------",
                PosixFilePermissions.toString(Files.getPosixFilePermissions(settingsStore.settingsFile())));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void saveTightensExistingFileToOwnerOnly() throws Exception {
        Path file = settingsStore.settingsFile();
        Files.writeString(file, "dataDir: /some/data/dir\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

        settingsStore.save(settingsStore.load());

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
    }

    @Test
    void isConfiguredFalseWhenDataDirBlank() {
        assertFalse(settingsStore.isConfigured());
    }

    @Test
    void isConfiguredTrueWhenDataDirSet() {
        AppSettings settings = new AppSettings();
        settings.setDataDir("/some/data/dir");
        settingsStore.save(settings);
        assertTrue(settingsStore.isConfigured());
    }

    @Test
    void resolvedDataDirExpandsHomePlaceholder() {
        AppSettings settings = new AppSettings();
        settings.setDataDir("${HOME}/git/triptale-data");
        String home = System.getProperty("user.home");
        assertTrue(settings.resolvedDataDir().isPresent());
        assertEquals(Path.of(home, "git", "triptale-data").toAbsolutePath().normalize(),
                settings.resolvedDataDir().get());
    }
}
