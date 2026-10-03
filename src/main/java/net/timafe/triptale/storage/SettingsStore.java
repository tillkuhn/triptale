package net.timafe.triptale.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Loads/saves {@code settings.yml} — app-wide, machine-local configuration (data dir, git
 * author, impressions patterns, attachment storage credentials) — from the settings directory
 * resolved by {@link TripTaleProperties#resolvedSettingsDir()}. The file is kept owner-only
 * ({@code rw-------}) where the file system supports POSIX permissions, since it holds a secret.
 *
 * <p>No JavaFX imports (storage package boundary rule).
 */
@Component
public class SettingsStore {

    private static final Logger log = LoggerFactory.getLogger(SettingsStore.class);
    private static final String SETTINGS_FILE = "settings.yml";
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    private final TripTaleProperties props;
    private final ObjectMapper yaml;

    public SettingsStore(TripTaleProperties props) {
        this.props = props;
        this.yaml = new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** Path to {@code settings.yml} under the resolved settings directory (for display purposes). */
    public Path settingsFile() {
        return props.resolvedSettingsDir().resolve(SETTINGS_FILE);
    }

    /**
     * Reads {@code settings.yml}, returning a blank/default {@link AppSettings} if the file
     * doesn't exist or can't be read (tolerant, mirrors the old prefs.yml loading behavior).
     */
    public AppSettings load() {
        Path file = settingsFile();
        if (!Files.exists(file)) return new AppSettings();
        try {
            AppSettings settings = yaml.readValue(file.toFile(), AppSettings.class);
            return settings == null ? new AppSettings() : settings;
        } catch (IOException e) {
            log.warn("Could not read {}: {}", file, e.getMessage());
            return new AppSettings();
        }
    }

    /** Writes the whole settings file back, creating the settings directory if needed. */
    public void save(AppSettings settings) {
        Path file = settingsFile();
        try {
            Files.createDirectories(file.getParent());
            String content = yaml.writeValueAsString(settings);
            restrictToOwner(file);
            Files.writeString(file, content);
            log.info("Saved settings to {}", file);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", file, e.getMessage());
        }
    }

    /** Creates the file owner-only, or tightens an existing one, before the secret is written. */
    private static void restrictToOwner(Path file) throws IOException {
        if (!file.getFileSystem().supportedFileAttributeViews().contains("posix")) return;
        if (Files.notExists(file)) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
        } else {
            Files.setPosixFilePermissions(file, OWNER_ONLY);
        }
    }

    /** True when a data directory has been configured (non-blank, resolvable). */
    public boolean isConfigured() {
        return load().resolvedDataDir().isPresent();
    }
}
