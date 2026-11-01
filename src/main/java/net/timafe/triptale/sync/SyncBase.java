package net.timafe.triptale.sync;

import java.util.Map;
import java.util.TreeMap;

/**
 * A device's record of what was identical locally and remotely at the end of its last
 * successful sync of one folder (todo 33c, ADR-0002): the bucket/prefix it was taken against,
 * and {@code relative path -> MD5 hex} for every file that matched on both sides back then.
 * Used by {@link BucketSyncer#plan} as the third point of the three-way merge — without it (a
 * missing, unreadable, or foreign-bucket base) the folder falls back to copy-both-ways, never
 * delete.
 *
 * <p>Immutable; {@link #empty()} never causes a delete, so a fresh device or a switched bucket
 * is always safe.
 */
public record SyncBase(String bucketUrl, String keyPrefix, Map<String, String> files) {

    public SyncBase {
        files = Map.copyOf(files);
    }

    public static SyncBase empty() {
        return new SyncBase("", "", Map.of());
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    /** This base with {@code relativePath} recorded as matching with the given MD5 hex. */
    public SyncBase withFile(String relativePath, String md5Hex) {
        Map<String, String> copy = new TreeMap<>(files);
        copy.put(relativePath, md5Hex);
        return new SyncBase(bucketUrl, keyPrefix, copy);
    }

    /** This base with {@code relativePath} dropped (deleted on both sides, or no longer tracked). */
    public SyncBase withoutFile(String relativePath) {
        if (!files.containsKey(relativePath)) return this;
        Map<String, String> copy = new TreeMap<>(files);
        copy.remove(relativePath);
        return new SyncBase(bucketUrl, keyPrefix, copy);
    }
}
