package net.timafe.triptale.storage;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

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

        settingsStore.save(settings);

        AppSettings loaded = settingsStore.load();
        assertEquals("/some/data/dir", loaded.getDataDir());
        assertEquals("Alice", loaded.getGit().getAuthorName());
        assertEquals("alice@example.com", loaded.getGit().getAuthorEmail());
        assertEquals("${HOME}/Pictures/output/${DATE}*.jpg", loaded.getImpressionsFilePattern());
        assertEquals(4, loaded.getImpressionsGridColumns());
        assertEquals("${HOME}/Pictures/00_Faves/${DATE}*.jpg", loaded.getImpressionsFaveFilePattern());
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
