package net.timafe.triptale.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;

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
    private String mapboxToken = "";
    private Attachments attachments = new Attachments();

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
    public String getMapboxToken() { return mapboxToken; }
    public void setMapboxToken(String mapboxToken) { this.mapboxToken = mapboxToken; }
    public Attachments getAttachments() { return attachments; }
    public void setAttachments(Attachments attachments) { this.attachments = attachments; }

    public static class Git {
        private String authorName = "";
        private String authorEmail = "";

        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public String getAuthorEmail() { return authorEmail; }
        public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
    }

    /** How the data dir's {@code attachments/} folder is synced (todo 33a). */
    public enum AttachmentSync {
        /** Local only — {@code attachments/} is gitignored. */
        @JsonProperty("off") OFF,
        /** Committed along with the {@code .md} files. */
        @JsonProperty("git") GIT,
        /** Gitignored locally, pushed to the S3 bucket. */
        @JsonProperty("cloud") CLOUD
    }

    /**
     * Sync mode and S3 storage for trip attachments (todo 33, D6; todo 33a). The secret is
     * stored in plain text, which is why {@code SettingsStore} keeps {@code settings.yml}
     * owner-only.
     */
    public static class Attachments {
        public static final String DEFAULT_REGION = "eu-central-1";
        // e.g. eu-central-1, us-gov-west-1, ap-southeast-2
        private static final Pattern REGION = Pattern.compile("[a-z]{2}(-[a-z]+)+-\\d+");

        private AttachmentSync sync = AttachmentSync.OFF;
        private String bucketUrl = "";
        private String region = DEFAULT_REGION;
        private String accessKeyId = "";
        private String secretAccessKey = "";

        /**
         * A user-facing message if {@link #bucketUrl} or {@link #region} is malformed, or if
         * {@code cloud} sync is selected without a complete S3 config; empty if valid. Otherwise
         * blank values are allowed, so the section can be filled in step by step.
         */
        public Optional<String> validationError() {
            if (sync == AttachmentSync.CLOUD && (isBlank(bucketUrl) || isBlank(region)
                    || isBlank(accessKeyId) || isBlank(secretAccessKey))) {
                return Optional.of("Cloud sync needs bucket URL, region, access key ID and secret");
            }
            if (bucketUrl != null && !bucketUrl.isBlank()) {
                try {
                    BucketUrl.parse(bucketUrl);
                } catch (IllegalArgumentException e) {
                    return Optional.of(e.getMessage());
                }
            }
            if (region != null && !region.isBlank() && !REGION.matcher(region).matches()) {
                return Optional.of("Invalid region '" + region + "' (e.g. " + DEFAULT_REGION + ")");
            }
            return Optional.empty();
        }

        private static boolean isBlank(String s) { return s == null || s.isBlank(); }

        public AttachmentSync getSync() { return sync; }
        public void setSync(AttachmentSync sync) { this.sync = sync == null ? AttachmentSync.OFF : sync; }
        public String getBucketUrl() { return bucketUrl; }
        public void setBucketUrl(String bucketUrl) { this.bucketUrl = bucketUrl; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public String getSecretAccessKey() { return secretAccessKey; }
        public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }
    }
}
