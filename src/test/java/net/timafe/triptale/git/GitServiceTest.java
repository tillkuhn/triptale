package net.timafe.triptale.git;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

    @TempDir
    Path tempDir;

    private Path dataDir;
    private SettingsStore settingsStore;
    private GitService gitService;
    private MarkdownStore store;

    @BeforeEach
    void setUp() {
        Path settingsDir = tempDir.resolve("settings");
        dataDir = tempDir.resolve("data");
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(settingsDir.toString());
        settingsStore = new SettingsStore(props);
        AppSettings settings = new AppSettings();
        settings.setDataDir(dataDir.toString());
        settingsStore.save(settings);
        store = new MarkdownStore(settingsStore);
        gitService = new GitService(settingsStore, store);
    }

    // -------------------------------------------------------------------------
    // initRepo — git init
    // -------------------------------------------------------------------------

    @Test
    void initRepoCreatesGitRepo() {
        gitService.initRepo();
        assertTrue(Files.isDirectory(dataDir.resolve(".git")),
                ".git directory should exist after initRepo");
    }

    @Test
    void initRepoIsIdempotent() {
        // Should not throw when .git already exists
        gitService.initRepo();
        assertDoesNotThrow(() -> gitService.initRepo());
    }

    @Test
    void initRepoWritesGitignore() throws Exception {
        gitService.initRepo();
        Path gitignore = dataDir.resolve(".gitignore");
        assertTrue(Files.exists(gitignore));
        assertTrue(Files.readString(gitignore).contains(".state.yml"));
    }

    @Test
    void initRepoWritesTolariaTypeDefinitions() throws Exception {
        gitService.initRepo();
        Path tripMd = dataDir.resolve("trip.md");
        Path taleMd = dataDir.resolve("tale.md");
        Path typeMd = dataDir.resolve("type.md");
        assertTrue(Files.exists(tripMd));
        assertTrue(Files.exists(taleMd));
        assertTrue(Files.exists(typeMd));
        assertTrue(Files.readString(tripMd).contains("type: Type"));
        assertTrue(Files.readString(taleMd).contains("type: Type"));
        assertTrue(Files.readString(typeMd).contains("type: Type"));
    }

    @Test
    void initRepoDoesNotOverwriteExistingTypeDefinitions() throws Exception {
        Files.createDirectories(dataDir);
        Path tripMd = dataDir.resolve("trip.md");
        Files.writeString(tripMd, "custom content");
        gitService.initRepo();
        assertEquals("custom content", Files.readString(tripMd));
    }

    // -------------------------------------------------------------------------
    // ensureGitignore — various pre-existing file states
    // -------------------------------------------------------------------------

    @Test
    void gitignoreEntryNotAddedWhenAlreadyPresent() throws Exception {
        Files.createDirectories(dataDir);
        Path gitignore = dataDir.resolve(".gitignore");
        Files.writeString(gitignore, ".state.yml\n");
        gitService.initRepo();
        // entry must appear exactly once
        String content = Files.readString(gitignore);
        assertEquals(1, content.lines().filter(".state.yml"::equals).count());
    }

    @Test
    void gitignoreEntryAppendedToExistingContentWithTrailingNewline() throws Exception {
        Files.createDirectories(dataDir);
        Path gitignore = dataDir.resolve(".gitignore");
        Files.writeString(gitignore, "*.log\n");
        gitService.initRepo();
        String content = Files.readString(gitignore);
        assertTrue(content.contains("*.log"));
        assertTrue(content.contains(".state.yml"));
    }

    @Test
    void gitignoreEntryAppendedToExistingContentWithoutTrailingNewline() throws Exception {
        Files.createDirectories(dataDir);
        Path gitignore = dataDir.resolve(".gitignore");
        Files.writeString(gitignore, "*.log");   // no trailing newline
        gitService.initRepo();
        String content = Files.readString(gitignore);
        assertTrue(content.contains("*.log"));
        assertTrue(content.contains(".state.yml"));
    }

    // -------------------------------------------------------------------------
    // commitAll — clean and dirty tree
    // -------------------------------------------------------------------------

    @Test
    void commitAllOnCleanTreeIsNoOp() {
        gitService.initRepo();
        // initRepo creates .gitignore, so the first commit picks that up;
        // a second commit on the now-clean tree should be a genuine no-op.
        assertNotNull(gitService.commitAll("first commit"));
        assertNull(gitService.commitAll("empty commit"));
    }

    @Test
    void commitAllCommitsStagedChanges() {
        gitService.initRepo();
        store.saveTrip(new Trip("tour", "Tour", LocalDate.of(2025, 6, 1), "test"));
        String sha = gitService.commitAll("add trip");
        assertNotNull(sha);
        assertEquals(7, sha.length());
        // second commit on now-clean tree should be a no-op and return null
        assertNull(gitService.commitAll("nothing new"));
    }

    @Test
    void commitAllWithAuthorConfigured() {
        AppSettings settingsWithAuthor = settingsStore.load();
        AppSettings.Git git = new AppSettings.Git();
        git.setAuthorName("Test User");
        git.setAuthorEmail("test@example.com");
        settingsWithAuthor.setGit(git);
        settingsStore.save(settingsWithAuthor);

        GitService svc = new GitService(settingsStore, store);
        svc.initRepo();
        store.saveEntry("tour",
                DiaryEntry.builder(LocalDate.of(2025, 6, 1)).tales("day 1").build());
        assertNotNull(svc.commitAll("entry with author"));
    }

    // -------------------------------------------------------------------------
    // remoteUrl — no remote configured
    // -------------------------------------------------------------------------

    @Test
    void remoteUrlReturnsEmptyWhenNoOrigin() {
        gitService.initRepo();
        assertEquals("", gitService.remoteUrl());
    }

    // -------------------------------------------------------------------------
    // push / pull — require origin, throw when missing
    // -------------------------------------------------------------------------

    @Test
    void pushThrowsGitExceptionWhenNoOrigin() {
        gitService.initRepo();
        assertThrows(GitException.class, () -> gitService.push());
    }

    @Test
    void pullThrowsGitExceptionWhenNoOrigin() {
        gitService.initRepo();
        assertThrows(GitException.class, () -> gitService.pull());
    }

    // -------------------------------------------------------------------------
    // GitException wraps message and cause correctly
    // -------------------------------------------------------------------------

    @Test
    void gitExceptionStoresMessageAndCause() {
        Throwable cause = new RuntimeException("root");
        GitException ex = new GitException("oops", cause);
        assertEquals("oops", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void gitExceptionAcceptsNullCause() {
        GitException ex = new GitException("no cause", null);
        assertEquals("no cause", ex.getMessage());
        assertNull(ex.getCause());
    }

    // -------------------------------------------------------------------------
    // isConfigured / needsInit
    // -------------------------------------------------------------------------

    @Test
    void isConfiguredTrueWhenDataDirSet() {
        assertTrue(gitService.isConfigured());
    }

    @Test
    void isConfiguredFalseWhenDataDirBlank() {
        Path unconfiguredSettingsDir = tempDir.resolve("unconfigured-settings");
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(unconfiguredSettingsDir.toString());
        SettingsStore unconfiguredSettingsStore = new SettingsStore(props);
        GitService svc = new GitService(unconfiguredSettingsStore, store);
        assertFalse(svc.isConfigured());
    }

    @Test
    void needsInitFalseWhenNotConfigured() {
        Path unconfiguredSettingsDir = tempDir.resolve("unconfigured-settings");
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(unconfiguredSettingsDir.toString());
        SettingsStore unconfiguredSettingsStore = new SettingsStore(props);
        GitService svc = new GitService(unconfiguredSettingsStore, store);
        assertFalse(svc.needsInit());
    }

    @Test
    void needsInitTrueWhenConfiguredButDataDirMissing() {
        // dataDir doesn't exist yet (BeforeEach only saves settings, doesn't create the dir)
        assertFalse(Files.exists(dataDir));
        assertTrue(gitService.needsInit());
    }

    @Test
    void needsInitFalseWhenConfiguredAndGitAlreadyInitialized() {
        gitService.initRepo();
        assertFalse(gitService.needsInit());
    }

    @Test
    void needsInitTrueWhenConfiguredAndDataDirEmpty() throws Exception {
        Files.createDirectories(dataDir);
        assertTrue(gitService.needsInit());
    }
}
