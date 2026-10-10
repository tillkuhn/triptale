package net.timafe.triptale.sync;

import java.nio.file.Path;
import java.util.Map;

/** The few bucket operations folder sync needs; {@link S3Client} is the real one. */
public interface ObjectStore {

    /** Size and ETag (without quotes) of a stored object. */
    record ObjectInfo(long size, String etag) {}

    /** All objects whose key starts with {@code keyPrefix}, by key. */
    Map<String, ObjectInfo> list(String keyPrefix);

    /** Uploads {@code file} as {@code key}; {@code md5} lets the store verify the upload. */
    void put(String key, Path file, byte[] md5);

    /** Downloads {@code key} into {@code target} (overwritten); the parent directory must exist. */
    void get(String key, Path target);
}
