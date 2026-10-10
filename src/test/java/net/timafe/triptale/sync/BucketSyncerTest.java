package net.timafe.triptale.sync;

import net.timafe.triptale.config.BucketUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BucketSyncerTest {

    private static final String PREFIX = "attachments/";

    @TempDir
    Path root;

    private final FakeStore store = new FakeStore();
    private final BucketSyncer syncer = new BucketSyncer(store);

    @Test
    void keyPrefix_withAndWithoutUrlPrefix() {
        assertEquals("attachments/", BucketSyncer.keyPrefix(BucketUrl.parse("s3://bucket"), "attachments"));
        assertEquals("triptale/attachments/",
                BucketSyncer.keyPrefix(BucketUrl.parse("s3://bucket/triptale/"), "attachments"));
        assertEquals("triptale/radio/", BucketSyncer.keyPrefix(BucketUrl.parse("s3://bucket/triptale"), "radio"));
    }

    @Test
    void push_uploadsMissingFilesWithForwardSlashKeys() throws Exception {
        write("2026/trip/2026-09-26-Saturday/track.gpx", "<gpx/>");
        write("2026/trip/notes.txt", "hi");

        BucketSyncer.Result result = syncer.push(root, PREFIX, (d, t, f) -> {});

        assertEquals(new BucketSyncer.Result(2, 0), result);
        assertEquals(List.of("attachments/2026/trip/2026-09-26-Saturday/track.gpx", "attachments/2026/trip/notes.txt"),
                store.putKeys);
    }

    @Test
    void push_skipsUnchangedAndUploadsChanged() throws Exception {
        Path same = write("2026/trip/day/same.jpg", "same");
        write("2026/trip/day/edited.jpg", "new!");
        store.remote.put("attachments/2026/trip/day/same.jpg", info(same));
        store.remote.put("attachments/2026/trip/day/edited.jpg", new ObjectStore.ObjectInfo(4, "00".repeat(16)));
        write("2026/trip/day/resized.jpg", "bigger");
        store.remote.put("attachments/2026/trip/day/resized.jpg", new ObjectStore.ObjectInfo(3, "x"));

        BucketSyncer.Result result = syncer.push(root, PREFIX, (d, t, f) -> {});

        assertEquals(new BucketSyncer.Result(2, 1), result);
        assertEquals(List.of("attachments/2026/trip/day/edited.jpg", "attachments/2026/trip/day/resized.jpg"),
                store.putKeys);
    }

    @Test
    void push_skipsHiddenFilesAndFolders() throws Exception {
        write(".gitignore", "*");
        write("2026/.DS_Store", "x");
        write("2026/trip/.cache/thumb.jpg", "x");
        write("2026/trip/day/photo.jpg", "x");

        syncer.push(root, PREFIX, (d, t, f) -> {});

        assertEquals(List.of("attachments/2026/trip/day/photo.jpg"), store.putKeys);
    }

    @Test
    void push_reportsProgressPerFile() throws Exception {
        write("a.txt", "a");
        write("b.txt", "b");
        List<String> calls = new ArrayList<>();

        syncer.push(root, PREFIX, (done, total, file) -> calls.add(done + "/" + total + " " + file));

        assertEquals(List.of("0/2 a.txt", "1/2 b.txt"), calls);
    }

    @Test
    void push_missingRootUploadsNothing() {
        BucketSyncer.Result result = syncer.push(root.resolve("nope"), PREFIX, (d, t, f) -> {});
        assertEquals(new BucketSyncer.Result(0, 0), result);
    }

    @Test
    void plan_countsDownloadsAndUploadsWithoutTransferring() throws Exception {
        Path same = write("2026/trip/day/same.jpg", "same");
        write("2026/trip/day/new.jpg", "12345");
        write("2026/trip/day/edited.jpg", "new!");
        store.remote.put("attachments/2026/trip/day/same.jpg", info(same));
        store.remote.put("attachments/2026/trip/day/edited.jpg", new ObjectStore.ObjectInfo(4, "00".repeat(16)));
        store.addRemote("attachments/2026/trip/day/cloud-only.jpg", "cloud-only");
        store.remote.put("attachments/2026/.DS_Store", new ObjectStore.ObjectInfo(3, "x"));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX);

        assertEquals(new BucketSyncer.Plan(1, 10, 2, 9), plan);
        assertEquals(List.of(), store.putKeys);
        assertEquals(List.of(), store.getKeys);
    }

    @Test
    void plan_upToDateWhenNothingToTransfer() throws Exception {
        Path same = write("a.txt", "a");
        store.remote.put("attachments/a.txt", info(same));
        assertTrue(syncer.plan(root, PREFIX).upToDate());
    }

    @Test
    void pull_downloadsOnlyMissingFilesAndCreatesFolders() throws Exception {
        Path local = write("2026/trip/day/local.jpg", "mine");
        store.addRemote("attachments/2026/trip/day/local.jpg", "theirs, different");
        store.addRemote("attachments/2026/trip/other-day/track.gpx", "<gpx/>");
        List<String> calls = new ArrayList<>();

        int downloaded = syncer.pull(root, PREFIX, (done, total, file) -> calls.add(done + "/" + total + " " + file));

        assertEquals(1, downloaded);
        assertEquals("<gpx/>", Files.readString(root.resolve("2026/trip/other-day/track.gpx")));
        assertEquals("mine", Files.readString(local), "an existing local file is never overwritten");
        assertEquals(List.of("attachments/2026/trip/other-day/track.gpx"), store.getKeys);
        assertEquals(List.of("0/1 2026/trip/other-day/track.gpx"), calls);
    }

    @Test
    void pull_skipsHiddenFolderMarkersAndEscapingKeys() {
        store.addRemote("attachments/.gitignore", "*");
        store.addRemote("attachments/2026/.cache/thumb.jpg", "x");
        store.addRemote("attachments/2026/trip/", "");
        store.addRemote("attachments/../evil.txt", "x");
        store.addRemote("attachments/2026/../../evil.txt", "x");
        store.addRemote("other/attachments/x.txt", "x");

        assertEquals(0, syncer.pull(root, PREFIX, (d, t, f) -> {}));
        assertEquals(List.of(), store.getKeys);
        assertFalse(Files.exists(root.getParent().resolve("evil.txt")));
    }

    @Test
    void pull_removesCorruptDownloadAndFails() {
        store.remote.put("attachments/bad.jpg", new ObjectStore.ObjectInfo(5, "00".repeat(16)));
        store.contents.put("attachments/bad.jpg", "hello");

        assertThrows(SyncException.class, () -> syncer.pull(root, PREFIX, (d, t, f) -> {}));
        assertFalse(Files.exists(root.resolve("bad.jpg")));
    }

    @Test
    void pull_acceptsMultipartEtagWithoutMd5Check() throws Exception {
        store.remote.put("attachments/big.mov", new ObjectStore.ObjectInfo(3, "abc123-4"));
        store.contents.put("attachments/big.mov", "mov");

        assertEquals(1, syncer.pull(root, PREFIX, (d, t, f) -> {}));
        assertEquals("mov", Files.readString(root.resolve("big.mov")));
    }

    @Test
    void localTarget_mapsKeysInsideRootOnly() {
        assertEquals(java.util.Optional.of(root.resolve("2026/t/d/a.jpg")),
                BucketSyncer.localTarget(root, PREFIX, "attachments/2026/t/d/a.jpg"));
        assertTrue(BucketSyncer.localTarget(root, PREFIX, "attachments/a\\b.jpg").isEmpty());
        assertTrue(BucketSyncer.localTarget(root, PREFIX, "attachments/").isEmpty());
        assertTrue(BucketSyncer.localTarget(root, PREFIX, "attachments//a.jpg").isEmpty());
    }

    private Path write(String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private static ObjectStore.ObjectInfo info(Path file) throws Exception {
        return new ObjectStore.ObjectInfo(Files.size(file), HexFormat.of().formatHex(BucketSyncer.md5(file)));
    }

    private static final class FakeStore implements ObjectStore {
        final Map<String, ObjectInfo> remote = new HashMap<>();
        final List<String> putKeys = new ArrayList<>();

        @Override public Map<String, ObjectInfo> list(String keyPrefix) {
            assertEquals(PREFIX, keyPrefix);
            return remote;
        }

        final Map<String, String> contents = new HashMap<>();
        final List<String> getKeys = new ArrayList<>();

        /** A remote object with real content, so its ETag is the content's MD5. */
        void addRemote(String key, String content) {
            contents.put(key, content);
            String md5 = HexFormat.of().formatHex(md5Of(content));
            remote.put(key, new ObjectInfo(content.getBytes(StandardCharsets.UTF_8).length, md5));
        }

        @Override public void put(String key, Path file, byte[] md5) {
            assertArrayEquals(BucketSyncer.md5(file), md5);
            putKeys.add(key);
        }

        @Override public void get(String key, Path target) {
            getKeys.add(key);
            try {
                Files.writeString(target, contents.get(key));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        private static byte[] md5Of(String content) {
            try {
                return java.security.MessageDigest.getInstance("MD5").digest(content.getBytes(StandardCharsets.UTF_8));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
