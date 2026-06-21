package net.timafe.triptale.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TripTalePropertiesTest {

    @Test
    void defaultDataDirResolvedToAbsolutePath() {
        TripTaleProperties props = new TripTaleProperties();
        Path resolved = props.resolvedDataDir();
        assertTrue(resolved.isAbsolute());
        assertTrue(resolved.toString().contains(".triptale"));
    }

    @Test
    void resolvedDataDirExpandsTilde() {
        TripTaleProperties props = new TripTaleProperties();
        props.setDataDir("~/.triptale");
        String home = System.getProperty("user.home");
        assertTrue(props.resolvedDataDir().startsWith(home));
    }

    @Test
    void resolvedDataDirUsesAbsolutePathDirectly() {
        TripTaleProperties props = new TripTaleProperties();
        props.setDataDir("/tmp/my-data");
        assertEquals("/tmp/my-data", props.resolvedDataDir().toString());
    }

    @Test
    void getAndSetDataDir() {
        TripTaleProperties props = new TripTaleProperties();
        props.setDataDir("/custom/path");
        assertEquals("/custom/path", props.getDataDir());
    }

    @Test
    void getAndSetGit() {
        TripTaleProperties props = new TripTaleProperties();
        TripTaleProperties.Git git = new TripTaleProperties.Git();
        git.setAuthorName("Alice");
        git.setAuthorEmail("alice@example.com");
        props.setGit(git);

        assertSame(git, props.getGit());
        assertEquals("Alice", props.getGit().getAuthorName());
        assertEquals("alice@example.com", props.getGit().getAuthorEmail());
    }

    @Test
    void gitDefaultsAreEmptyStrings() {
        TripTaleProperties.Git git = new TripTaleProperties.Git();
        assertEquals("", git.getAuthorName());
        assertEquals("", git.getAuthorEmail());
    }
}
