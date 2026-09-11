package net.timafe.triptale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Bootstrapping-only Spring configuration. The app needs to know where to look for
 * {@code settings.yml} before it can load anything else; every other property (data dir, git
 * author, impressions patterns) lives in {@code settings.yml} (see {@code AppSettings} /
 * {@code SettingsStore}) and is editable at runtime instead of being fixed at launch time.
 */
@ConfigurationProperties(prefix = "triptale")
public class TripTaleProperties {

    private String settingsDir = "";

    /**
     * Resolves the settings directory. Blank {@link #settingsDir} means "use the platform
     * default": {@code %APPDATA%/triptale} on Windows, {@code $HOME/.config/triptale} on macOS
     * and Linux. An explicit value expands a leading {@code ~} and is otherwise used as-is.
     */
    public Path resolvedSettingsDir() {
        if (settingsDir == null || settingsDir.isBlank()) {
            return platformDefaultSettingsDir();
        }
        String expanded = settingsDir.startsWith("~")
                ? System.getProperty("user.home") + settingsDir.substring(1)
                : settingsDir;
        return Paths.get(expanded).toAbsolutePath().normalize();
    }

    private static Path platformDefaultSettingsDir() {
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, "triptale").toAbsolutePath().normalize();
            }
        }
        return Paths.get(System.getProperty("user.home"), ".config", "triptale")
                .toAbsolutePath().normalize();
    }

    private static boolean isWindows() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) return true;
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase().contains("win");
    }

    public String getSettingsDir() { return settingsDir; }
    public void setSettingsDir(String settingsDir) { this.settingsDir = settingsDir; }
}
