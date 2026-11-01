package net.timafe.triptale.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SyncBaseStoreTest {

    @TempDir
    Path dir;

    private final SyncBaseStore store = new SyncBaseStore();

    @Test
    void load_missingFileIsEmptyButTaggedWithTheRequestedBucket() {
        SyncBase loaded = store.load(dir.resolve("none.yml"), "s3://bucket", "attachments/");
        assertTrue(loaded.isEmpty());
        // Regression: an untagged empty() base would never load successfully again once saved,
        // since BucketSyncer.sync() carries a base's own bucketUrl/keyPrefix forward.
        assertEquals("s3://bucket", loaded.bucketUrl());
        assertEquals("attachments/", loaded.keyPrefix());
    }

    @Test
    void load_corruptFileIsEmptyButTagged() throws Exception {
        Path file = dir.resolve("base.yml");
        Files.writeString(file, "not: [valid, {yaml");
        SyncBase loaded = store.load(file, "s3://bucket", "attachments/");
        assertTrue(loaded.isEmpty());
        assertEquals("s3://bucket", loaded.bucketUrl());
        assertEquals("attachments/", loaded.keyPrefix());
    }

    @Test
    void load_differentBucketOrPrefixIsEmptyButRetagged() throws Exception {
        Path file = dir.resolve("base.yml");
        SyncBase base = new SyncBase("s3://bucket", "attachments/", Map.of("a.jpg", "abc"));
        store.save(file, base);

        SyncBase loadedOtherBucket = store.load(file, "s3://other-bucket", "attachments/");
        assertTrue(loadedOtherBucket.isEmpty());
        assertEquals("s3://other-bucket", loadedOtherBucket.bucketUrl());

        SyncBase loadedOtherPrefix = store.load(file, "s3://bucket", "radio/");
        assertTrue(loadedOtherPrefix.isEmpty());
        assertEquals("radio/", loadedOtherPrefix.keyPrefix());
    }

    /**
     * Regression for a bug where the very first sync (no file yet) wrote the base back with a
     * blank bucketUrl/keyPrefix, which then made every later load treat it as foreign and reset
     * forever — silently disabling delete propagation for good.
     */
    @Test
    void firstSyncSelfHeals_secondLoadRecognizesTheBase() throws Exception {
        Path file = dir.resolve("base.yml");
        SyncBase first = store.load(file, "s3://bucket", "attachments/");
        SyncBase afterFirstSync = first.withFile("a.jpg", "abc");
        store.save(file, afterFirstSync);

        SyncBase second = store.load(file, "s3://bucket", "attachments/");

        assertFalse(second.isEmpty());
        assertEquals(Map.of("a.jpg", "abc"), second.files());
    }

    @Test
    void save_thenLoad_roundTrips() throws Exception {
        Path file = dir.resolve("base.yml");
        SyncBase base = new SyncBase("s3://bucket", "attachments/", Map.of("a.jpg", "abc", "b/c.jpg", "def"));
        store.save(file, base);

        SyncBase loaded = store.load(file, "s3://bucket", "attachments/");

        assertEquals(base.files(), loaded.files());
        assertEquals("s3://bucket", loaded.bucketUrl());
        assertEquals("attachments/", loaded.keyPrefix());
    }

    @Test
    void save_createsParentDirectories() throws Exception {
        Path file = dir.resolve("nested/dir/base.yml");
        store.save(file, new SyncBase("s3://bucket", "attachments/", Map.of()));
        assertTrue(Files.exists(file));
    }
}
