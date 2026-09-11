package net.timafe.triptale.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TripTalePropertiesTest {

    @Test
    void blankSettingsDirResolvesToPlatformDefault() {
        TripTaleProperties props = new TripTaleProperties();
        Path resolved = props.resolvedSettingsDir();
        assertTrue(resolved.isAbsolute());
        // On Windows this would be %APPDATA%/triptale instead — this test only asserts the
        // non-Windows branch (.config/triptale), matching this project's CI/dev environments.
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWindows) {
            assertTrue(resolved.toString().contains(".config"));
            assertTrue(resolved.toString().endsWith("triptale"));
        }
    }

    @Test
    void explicitSettingsDirExpandsTilde() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir("~/.triptale-settings");
        String home = System.getProperty("user.home");
        assertTrue(props.resolvedSettingsDir().startsWith(home));
        assertTrue(props.resolvedSettingsDir().toString().endsWith(".triptale-settings"));
    }

    @Test
    void explicitAbsoluteSettingsDirUsedDirectly() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir("/tmp/my-settings");
        assertEquals("/tmp/my-settings", props.resolvedSettingsDir().toString());
    }

    @Test
    void getAndSetSettingsDir() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir("/custom/path");
        assertEquals("/custom/path", props.getSettingsDir());
    }

    @Test
    void settingsDirDefaultsToBlank() {
        TripTaleProperties props = new TripTaleProperties();
        assertEquals("", props.getSettingsDir());
    }
}
