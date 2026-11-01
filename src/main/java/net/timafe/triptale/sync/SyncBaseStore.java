package net.timafe.triptale.sync;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads/saves a {@link SyncBase} as a gitignored YAML file in the data dir root, e.g.
 * {@code .attachments-sync.yml} or {@code .radio-sync.yml} (todo 33c). Never throws for a
 * missing, unreadable, or foreign-bucket base — {@link #load} returns an empty base <em>tagged
 * with the given {@code bucketUrl}/{@code keyPrefix}</em>, which is always safe for the run that
 * loads it (no files recorded means no delete can be classified, see ADR-0002) but still lets
 * the base self-heal: the first sync after a reset writes every file back with the correct tag,
 * so the next load recognizes it again. Returning the untagged {@link SyncBase#empty()} here
 * would be a trap — {@link BucketSyncer#sync} carries a base's own bucket/prefix forward via
 * {@link SyncBase#withFile}, so a blank tag would stick forever and the base would never load
 * successfully again.
 */
public final class SyncBaseStore {

    private static final Logger log = LoggerFactory.getLogger(SyncBaseStore.class);

    /** Plain data shape of the YAML file; {@link SyncBase} itself stays an immutable record. */
    public static final class Data {
        @JsonProperty("bucketUrl") public String bucketUrl = "";
        @JsonProperty("keyPrefix") public String keyPrefix = "";
        @JsonProperty("files") public Map<String, String> files = new TreeMap<>();
    }

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Loads {@code file}, treating it as empty (but tagged with {@code bucketUrl}/{@code keyPrefix}
     * for next time) unless it names the same bucket/prefix already — a different bucket/prefix
     * (or a missing/corrupt file) can never cause a delete.
     */
    public SyncBase load(Path file, String bucketUrl, String keyPrefix) {
        if (!Files.exists(file)) return new SyncBase(bucketUrl, keyPrefix, Map.of());
        try {
            Data data = yaml.readValue(file.toFile(), Data.class);
            if (data == null || !bucketUrl.equals(data.bucketUrl) || !keyPrefix.equals(data.keyPrefix)) {
                return new SyncBase(bucketUrl, keyPrefix, Map.of());
            }
            return new SyncBase(bucketUrl, keyPrefix, data.files == null ? Map.of() : data.files);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read {}: {}", file, e.getMessage());
            return new SyncBase(bucketUrl, keyPrefix, Map.of());
        }
    }

    public void save(Path file, SyncBase base) {
        Data data = new Data();
        data.bucketUrl = base.bucketUrl();
        data.keyPrefix = base.keyPrefix();
        data.files = new TreeMap<>(base.files());
        try {
            Files.createDirectories(file.getParent());
            yaml.writeValue(file.toFile(), data);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", file, e.getMessage());
        }
    }
}
