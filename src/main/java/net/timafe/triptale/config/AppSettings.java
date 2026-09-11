package net.timafe.triptale.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * App-wide user settings, persisted as {@code settings.yml} under the resolved settings
 * directory (see {@code TripTaleProperties.resolvedSettingsDir()}), loaded/saved by
 * {@code net.timafe.triptale.storage.SettingsStore}.
 *
 * <p>Plain POJO for Jackson YAML (de)serialization — no JavaFX, no Spring annotations. Not a
 * Spring-managed {@code @ConfigurationProperties} bean: it's user-editable at runtime via the
 * Edit Settings dialog, not fixed at launch.
 */
public class AppSettings {

    private String dataDir = "";
    private Git git = new Git();
    private String impressionsFilePattern = "";
    private int impressionsGridColumns = 2;
    private String impressionsFaveFilePattern = "";

    /**
     * Resolves {@link #dataDir} to an absolute, normalized path. Supports {@code ${HOME}}
     * expansion (this project's established placeholder convention, see
     * {@code ImpressionsResolver}) and, for good measure, legacy leading {@code ~} expansion.
     * Returns {@link Optional#empty()} when {@link #dataDir} is blank (unconfigured) —
     * deliberately no default fallback.
     */
    public Optional<Path> resolvedDataDir() {
        if (dataDir == null || dataDir.isBlank()) return Optional.empty();
        String home = System.getProperty("user.home", "");
        String expanded = dataDir.replace("${HOME}", home);
        if (expanded.startsWith("~")) {
            expanded = home + expanded.substring(1);
        }
        return Optional.of(Paths.get(expanded).toAbsolutePath().normalize());
    }

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public Git getGit() { return git; }
    public void setGit(Git git) { this.git = git; }
    public String getImpressionsFilePattern() { return impressionsFilePattern; }
    public void setImpressionsFilePattern(String impressionsFilePattern) { this.impressionsFilePattern = impressionsFilePattern; }
    public int getImpressionsGridColumns() { return impressionsGridColumns; }
    public void setImpressionsGridColumns(int impressionsGridColumns) { this.impressionsGridColumns = impressionsGridColumns; }
    public String getImpressionsFaveFilePattern() { return impressionsFaveFilePattern; }
    public void setImpressionsFaveFilePattern(String impressionsFaveFilePattern) { this.impressionsFaveFilePattern = impressionsFaveFilePattern; }

    public static class Git {
        private String authorName = "";
        private String authorEmail = "";

        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public String getAuthorEmail() { return authorEmail; }
        public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
    }
}
