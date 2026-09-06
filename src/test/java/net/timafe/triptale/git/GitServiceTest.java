package net.timafe.triptale.git;

import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.MarkdownStore;
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

    private GitService gitService;
    private MarkdownStore store;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setDataDir(tempDir.toString());
        store = new MarkdownStore(props);
        gitService = new GitService(props, store);
    }

    // -------------------------------------------------------------------------
    // initOnStartup — git init
    // -------------------------------------------------------------------------

    @Test
    void initOnStartupCreatesGitRepo() {
        gitService.initOnStartup();
        assertTrue(Files.isDirectory(tempDir.resolve(".git")),
                ".git directory should exist after initOnStartup");
    }

    @Test
    void initOnStartupIsIdempotent() {
        // Should not throw when .git already exists
        gitService.initOnStartup();
        assertDoesNotThrow(() -> gitService.initOnStartup());
    }

    @Test
    void initOnStartupWritesGitignore() throws Exception {
        gitService.initOnStartup();
        Path gitignore = tempDir.resolve(".gitignore");
        assertTrue(Files.exists(gitignore));
        assertTrue(Files.readString(gitignore).contains("prefs.yml"));
    }

    @Test
    void initOnStartupWritesTolariaTypeDefinitions() throws Exception {
        gitService.initOnStartup();
        Path tripMd = tempDir.resolve("trip.md");
        Path taleMd = tempDir.resolve("tale.md");
        assertTrue(Files.exists(tripMd));
        assertTrue(Files.exists(taleMd));
        assertTrue(Files.readString(tripMd).contains("type: Type"));
        assertTrue(Files.readString(taleMd).contains("type: Type"));
    }

    @Test
    void initOnStartupDoesNotOverwriteExistingTypeDefinitions() throws Exception {
        Path tripMd = tempDir.resolve("trip.md");
        Files.writeString(tripMd, "custom content");
        gitService.initOnStartup();
        assertEquals("custom content", Files.readString(tripMd));
    }

    // -------------------------------------------------------------------------
    // ensureGitignore — various pre-existing file states
    // -------------------------------------------------------------------------

    @Test
    void gitignoreEntryNotAddedWhenAlreadyPresent() throws Exception {
        Path gitignore = tempDir.resolve(".gitignore");
        Files.writeString(gitignore, "prefs.yml\n");
        gitService.initOnStartup();
        // entry must appear exactly once
        String content = Files.readString(gitignore);
        assertEquals(1, content.lines().filter("prefs.yml"::equals).count());
    }

    @Test
    void gitignoreEntryAppendedToExistingContentWithTrailingNewline() throws Exception {
        Path gitignore = tempDir.resolve(".gitignore");
        Files.writeString(gitignore, "*.log\n");
        gitService.initOnStartup();
        String content = Files.readString(gitignore);
        assertTrue(content.contains("*.log"));
        assertTrue(content.contains("prefs.yml"));
    }

    @Test
    void gitignoreEntryAppendedToExistingContentWithoutTrailingNewline() throws Exception {
        Path gitignore = tempDir.resolve(".gitignore");
        Files.writeString(gitignore, "*.log");   // no trailing newline
        gitService.initOnStartup();
        String content = Files.readString(gitignore);
        assertTrue(content.contains("*.log"));
        assertTrue(content.contains("prefs.yml"));
    }

    // -------------------------------------------------------------------------
    // commitAll — clean and dirty tree
    // -------------------------------------------------------------------------

    @Test
    void commitAllOnCleanTreeIsNoOp() {
        gitService.initOnStartup();
        // initOnStartup creates .gitignore, so the first commit picks that up;
        // a second commit on the now-clean tree should be a genuine no-op.
        assertNotNull(gitService.commitAll("first commit"));
        assertNull(gitService.commitAll("empty commit"));
    }

    @Test
    void commitAllCommitsStagedChanges() {
        gitService.initOnStartup();
        store.saveTrip(new Trip("tour", "Tour", LocalDate.of(2025, 6, 1), "test"));
        String sha = gitService.commitAll("add trip");
        assertNotNull(sha);
        assertEquals(7, sha.length());
        // second commit on now-clean tree should be a no-op and return null
        assertNull(gitService.commitAll("nothing new"));
    }

    @Test
    void commitAllWithAuthorConfigured() {
        TripTaleProperties propsWithAuthor = new TripTaleProperties();
        propsWithAuthor.setDataDir(tempDir.toString());
        TripTaleProperties.Git git = new TripTaleProperties.Git();
        git.setAuthorName("Test User");
        git.setAuthorEmail("test@example.com");
        propsWithAuthor.setGit(git);

        GitService svc = new GitService(propsWithAuthor, store);
        svc.initOnStartup();
        store.saveEntry("tour",
                DiaryEntry.builder(LocalDate.of(2025, 6, 1)).tales("day 1").build());
        assertNotNull(svc.commitAll("entry with author"));
    }

    // -------------------------------------------------------------------------
    // remoteUrl — no remote configured
    // -------------------------------------------------------------------------

    @Test
    void remoteUrlReturnsEmptyWhenNoOrigin() {
        gitService.initOnStartup();
        assertEquals("", gitService.remoteUrl());
    }

    // -------------------------------------------------------------------------
    // push / pull — require origin, throw when missing
    // -------------------------------------------------------------------------

    @Test
    void pushThrowsGitExceptionWhenNoOrigin() {
        gitService.initOnStartup();
        assertThrows(GitException.class, () -> gitService.push());
    }

    @Test
    void pullThrowsGitExceptionWhenNoOrigin() {
        gitService.initOnStartup();
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
}
