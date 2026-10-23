package net.timafe.triptale.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.net.URISyntaxException;
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
 *
 * <p>Unknown keys are ignored, so a {@code settings.yml} written by an older version (e.g. with
 * the removed {@code impressionsFaveFilePattern}) still loads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppSettings {

    public static final String DEFAULT_IMPRESSIONS_BASE_FILTER = "*.jpg *.jpeg *.png";

    private String dataDir = "";
    private Git git = new Git();
    private String impressionsFilePattern = "";
    private int impressionsGridColumns = 2;
    private String impressionsBaseFilter = DEFAULT_IMPRESSIONS_BASE_FILTER;
    private String impressionsFaveFilter = "";
    private String mapboxToken = "";
    private Attachments attachments = new Attachments();
    private boolean radioEnabled = false;
    private String defaultTripLanguage = "en";
    private WordPress wordPress = new WordPress();
    private String telegramBotToken = "";
    private String telegramChatId = "";

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
    /** Space-separated filename globs every impression source is reduced to first (todo 47). */
    public String getImpressionsBaseFilter() { return impressionsBaseFilter; }
    public void setImpressionsBaseFilter(String impressionsBaseFilter) { this.impressionsBaseFilter = impressionsBaseFilter == null ? "" : impressionsBaseFilter; }
    /** Space-separated filename globs marking faves, e.g. {@code *+.*}; blank = none defined (todo 47). */
    public String getImpressionsFaveFilter() { return impressionsFaveFilter; }
    public void setImpressionsFaveFilter(String impressionsFaveFilter) { this.impressionsFaveFilter = impressionsFaveFilter == null ? "" : impressionsFaveFilter; }
    public String getMapboxToken() { return mapboxToken; }
    public void setMapboxToken(String mapboxToken) { this.mapboxToken = mapboxToken; }
    public Attachments getAttachments() { return attachments; }
    public void setAttachments(Attachments attachments) { this.attachments = attachments; }
    /** Gates the Radio feature (todo 46): off by default, UI and folder stay untouched until a restart with this on. */
    public boolean isRadioEnabled() { return radioEnabled; }
    public void setRadioEnabled(boolean radioEnabled) { this.radioEnabled = radioEnabled; }
    /** Language code ({@code en}, {@code de}) for words the exports add around the tales (todo 30); kept as a string so a hand-edited unknown value doesn't break loading. */
    public String getDefaultTripLanguage() { return defaultTripLanguage; }
    public void setDefaultTripLanguage(String defaultTripLanguage) { this.defaultTripLanguage = defaultTripLanguage == null ? "" : defaultTripLanguage; }
    public WordPress getWordPress() { return wordPress; }
    public void setWordPress(WordPress wordPress) { this.wordPress = wordPress == null ? new WordPress() : wordPress; }
    /** Bot token from @BotFather for the mobile inbox (todo 68); blank = feature off. */
    public String getTelegramBotToken() { return telegramBotToken; }
    public void setTelegramBotToken(String telegramBotToken) { this.telegramBotToken = telegramBotToken == null ? "" : telegramBotToken; }
    /** The single chat ID the inbox accepts messages from (todo 68); blank = feature off. */
    public String getTelegramChatId() { return telegramChatId; }
    public void setTelegramChatId(String telegramChatId) { this.telegramChatId = telegramChatId == null ? "" : telegramChatId; }

    public static class Git {
        private String authorName = "";
        private String authorEmail = "";

        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public String getAuthorEmail() { return authorEmail; }
        public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
    }

    /**
     * WordPress.com OAuth2 app registration and token for "Send to WordPress" (todo 60). The
     * client secret and token are stored in plain text, which is why {@code SettingsStore} keeps
     * {@code settings.yml} owner-only.
     */
    public static class WordPress {
        private String site = "";
        private String clientId = "";
        private String clientSecret = "";
        private String accessToken = "";

        /**
         * A user-facing message if the fields needed to start the OAuth2 flow are incomplete;
         * empty if valid. {@link #accessToken} is excluded — it's obtained through that flow,
         * not typed in, so its absence is the normal "not connected yet" state, not an error.
         */
        public Optional<String> validationError() {
            boolean anySet = !isBlank(site) || !isBlank(clientId) || !isBlank(clientSecret);
            if (anySet && (isBlank(site) || isBlank(clientId) || isBlank(clientSecret))) {
                return Optional.of("WordPress needs a site, client ID and client secret to connect");
            }
            return Optional.empty();
        }

        private static boolean isBlank(String s) { return s == null || s.isBlank(); }

        /** The WordPress.com site, e.g. {@code timafe.wordpress.com}. */
        public String getSite() { return site; }
        public void setSite(String site) { this.site = site == null ? "" : site; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId == null ? "" : clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret == null ? "" : clientSecret; }
        /** Set once the OAuth2 authorization-code flow (todo 60) completes; blank = not connected. */
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken == null ? "" : accessToken; }
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
        // Optional public HTTPS base URL that maps to attachments/, e.g. a CloudFront distribution (todo 44)
        private String publicBaseUrl = "";

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
            if (!isBlank(publicBaseUrl) && !isHttpsUrl(publicBaseUrl)) {
                return Optional.of("Backpack public URL must be an https:// URL, e.g. https://d123.cloudfront.net/");
            }
            return Optional.empty();
        }

        private static boolean isBlank(String s) { return s == null || s.isBlank(); }

        private static boolean isHttpsUrl(String s) {
            try {
                URI uri = new URI(s.trim());
                return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                        && uri.getQuery() == null && uri.getFragment() == null;
            } catch (URISyntaxException e) {
                return false;
            }
        }

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
        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl; }
    }
}
