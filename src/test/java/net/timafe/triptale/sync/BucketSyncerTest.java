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
    private final FakeTrash trash = new FakeTrash();
    private final BucketSyncer syncer = new BucketSyncer(store);

    @Test
    void keyPrefix_withAndWithoutUrlPrefix() {
        assertEquals("attachments/", BucketSyncer.keyPrefix(BucketUrl.parse("s3://bucket"), "attachments"));
        assertEquals("triptale/attachments/",
                BucketSyncer.keyPrefix(BucketUrl.parse("s3://bucket/triptale/"), "attachments"));
        assertEquals("triptale/radio/", BucketSyncer.keyPrefix(BucketUrl.parse("s3://bucket/triptale"), "radio"));
    }

    // ---------------------------------------------------------------------
    // No base: copy both ways, never delete (todo 33a/33b behavior)
    // ---------------------------------------------------------------------

    @Test
    void plan_withoutBase_newLocalFileIsUploaded() throws Exception {
        write("2026/trip/day/track.gpx", "<gpx/>");

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, SyncBase.empty());

        assertEquals(1, plan.toUpload());
        assertEquals(0, plan.toDownload());
        assertEquals(0, plan.totalDeletes());
    }

    @Test
    void plan_withoutBase_remoteOnlyFileIsDownloaded() {
        store.addRemote("attachments/2026/trip/day/track.gpx", "<gpx/>");

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, SyncBase.empty());

        assertEquals(1, plan.toDownload());
        assertEquals(0, plan.toUpload());
    }

    @Test
    void plan_withoutBase_identicalFileNeedsNoTransfer() throws Exception {
        Path same = write("same.jpg", "same");
        store.remote.put("attachments/same.jpg", info(same));

        assertTrue(syncer.plan(root, PREFIX, SyncBase.empty()).upToDate());
    }

    @Test
    void plan_withoutBase_differingOnBothSidesUploadsLocal() throws Exception {
        write("edited.jpg", "new!");
        store.remote.put("attachments/edited.jpg", new ObjectStore.ObjectInfo(4, "00".repeat(16)));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, SyncBase.empty());

        assertEquals(1, plan.toUpload());
        assertEquals(0, plan.deleteLocal() + plan.deleteRemote());
    }

    @Test
    void plan_neverDeletesWithoutABase() throws Exception {
        // Nothing local, something remote that isn't "new" conceptually but there's no base to
        // compare against, so it's just a download, never interpreted as "deleted here".
        store.addRemote("attachments/gone.jpg", "x");
        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, SyncBase.empty());
        assertEquals(0, plan.totalDeletes());
    }

    @Test
    void plan_skipsHiddenFilesAndFolders() throws Exception {
        write(".gitignore", "*");
        write("2026/.DS_Store", "x");
        write("2026/trip/.cache/thumb.jpg", "x");
        write("2026/trip/day/photo.jpg", "x");

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, SyncBase.empty());

        assertEquals(List.of("2026/trip/day/photo.jpg"),
                plan.of(BucketSyncer.Action.UPLOAD).stream().map(BucketSyncer.FileAction::relativePath).toList());
    }

    // ---------------------------------------------------------------------
    // With a base: three-way merge (todo 33c, ADR-0002)
    // ---------------------------------------------------------------------

    private SyncBase baseWith(Map<String, String> files) {
        return new SyncBase("s3://bucket", PREFIX, files);
    }

    @Test
    void plan_deletedLocallyAndUnchangedRemotely_deletesRemote() throws Exception {
        // was in base, local file removed since, remote still has the original content
        store.addRemote("attachments/gone.jpg", "original");
        SyncBase base = baseWith(Map.of("gone.jpg", md5Hex("original")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);

        assertEquals(List.of("gone.jpg"),
                plan.of(BucketSyncer.Action.DELETE_REMOTE).stream().map(BucketSyncer.FileAction::relativePath).toList());
    }

    @Test
    void plan_deletedRemotelyAndUnchangedLocally_deletesLocal() throws Exception {
        write("gone.jpg", "original");
        SyncBase base = baseWith(Map.of("gone.jpg", md5Hex("original")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);

        assertEquals(List.of("gone.jpg"),
                plan.of(BucketSyncer.Action.DELETE_LOCAL).stream().map(BucketSyncer.FileAction::relativePath).toList());
    }

    @Test
    void plan_deletedLocallyButEditedRemotely_downloadsTheEdit() throws Exception {
        store.addRemote("attachments/edited.jpg", "new content");
        SyncBase base = baseWith(Map.of("edited.jpg", md5Hex("original")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);

        assertEquals(List.of("edited.jpg"),
                plan.of(BucketSyncer.Action.DOWNLOAD).stream().map(BucketSyncer.FileAction::relativePath).toList());
    }

    @Test
    void plan_deletedRemotelyButEditedLocally_uploadsTheEdit() throws Exception {
        write("edited.jpg", "new content");
        SyncBase base = baseWith(Map.of("edited.jpg", md5Hex("original")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);

        assertEquals(List.of("edited.jpg"),
                plan.of(BucketSyncer.Action.UPLOAD).stream().map(BucketSyncer.FileAction::relativePath).toList());
    }

    @Test
    void plan_editedRemotelyOnly_downloads() throws Exception {
        write("a.jpg", "original");
        store.addRemote("attachments/a.jpg", "edited remotely");
        SyncBase base = baseWith(Map.of("a.jpg", md5Hex("original")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);

        assertEquals(1, plan.toDownload());
        assertEquals(0, plan.toUpload());
    }

    @Test
    void plan_editedLocallyOnly_uploads() throws Exception {
        write("a.jpg", "edited locally");
        store.addRemote("attachments/a.jpg", "original");
        SyncBase base = baseWith(Map.of("a.jpg", md5Hex("original")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);

        assertEquals(0, plan.toDownload());
        assertEquals(1, plan.toUpload());
    }

    @Test
    void plan_editedOnBothSides_localWins() throws Exception {
        write("a.jpg", "edited locally");
        store.addRemote("attachments/a.jpg", "edited remotely");
        SyncBase base = baseWith(Map.of("a.jpg", md5Hex("original")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);

        assertEquals(1, plan.toUpload());
        assertEquals(0, plan.toDownload());
    }

    @Test
    void plan_deletedOnBothSides_dropsFromBase() throws Exception {
        SyncBase base = baseWith(Map.of("gone.jpg", md5Hex("original")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);

        assertEquals(1, plan.of(BucketSyncer.Action.DROP_BASE).size());
        assertTrue(plan.upToDate());
    }

    @Test
    void plan_unchangedOnBothSides_isNoop() throws Exception {
        write("a.jpg", "same");
        store.remote.put("attachments/a.jpg", info(root.resolve("a.jpg")));
        SyncBase base = baseWith(Map.of("a.jpg", md5Hex("same")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);

        assertEquals(1, plan.of(BucketSyncer.Action.NOOP).size());
        assertTrue(plan.upToDate());
    }

    @Test
    void plan_exceedsDeleteThreshold_byCount() {
        Map<String, String> files = new HashMap<>();
        for (int i = 0; i < 21; i++) {
            String key = "file" + i + ".jpg";
            store.addRemote("attachments/" + key, "content" + i);
            files.put(key, md5Hex("content" + i));
        }
        SyncBase base = baseWith(files);
        // every file deleted locally, none changed remotely -> 21 remote deletes
        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);
        assertTrue(plan.exceedsDeleteThreshold());
    }

    @Test
    void plan_belowDeleteThreshold_doesNotFlag() throws Exception {
        write("a.jpg", "same");
        write("b.jpg", "same2");
        store.remote.put("attachments/a.jpg", info(root.resolve("a.jpg")));
        store.remote.put("attachments/b.jpg", info(root.resolve("b.jpg")));
        SyncBase base = baseWith(Map.of("a.jpg", md5Hex("same"), "b.jpg", md5Hex("same2")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);

        assertFalse(plan.exceedsDeleteThreshold());
    }

    // ---------------------------------------------------------------------
    // sync(): executes a plan and updates the base
    // ---------------------------------------------------------------------

    @Test
    void sync_uploadsDownloadsAndDeletesPerPlan() throws Exception {
        write("new-local.jpg", "local content");
        write("deleted-remotely.jpg", "unchanged");
        store.addRemote("attachments/new-remote.jpg", "remote content");
        store.addRemote("attachments/deleted-locally.jpg", "unchanged");
        SyncBase base = baseWith(Map.of(
                "deleted-remotely.jpg", md5Hex("unchanged"),
                "deleted-locally.jpg", md5Hex("unchanged")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);
        List<SyncBase> checkpoints = new ArrayList<>();
        BucketSyncer.Result result = syncer.sync(root, PREFIX, base, plan, trash, (d, t, f) -> {}, checkpoints::add);

        assertEquals(new BucketSyncer.Result(1, 1, 1, 1), result);
        assertEquals("remote content", Files.readString(root.resolve("new-remote.jpg")));
        assertEquals(List.of("attachments/new-local.jpg"), store.putKeys);
        assertEquals(List.of("attachments/deleted-locally.jpg"), store.deletedKeys);
        assertEquals(List.of(root.resolve("deleted-remotely.jpg")), trash.trashed);
        assertFalse(checkpoints.isEmpty(), "base should be checkpointed as files finish");

        SyncBase finalBase = checkpoints.get(checkpoints.size() - 1);
        assertTrue(finalBase.files().containsKey("new-local.jpg"));
        assertTrue(finalBase.files().containsKey("new-remote.jpg"));
        assertFalse(finalBase.files().containsKey("deleted-remotely.jpg"));
        assertFalse(finalBase.files().containsKey("deleted-locally.jpg"));
    }

    @Test
    void sync_recordsNoopAndDropsBaseEntries() throws Exception {
        write("unchanged.jpg", "same");
        store.remote.put("attachments/unchanged.jpg", info(root.resolve("unchanged.jpg")));
        SyncBase base = baseWith(Map.of("unchanged.jpg", md5Hex("same"), "gone.jpg", md5Hex("x")));

        BucketSyncer.Plan plan = syncer.plan(root, PREFIX, base);
        SyncBase[] last = new SyncBase[1];
        syncer.sync(root, PREFIX, base, plan, trash, (d, t, f) -> {}, b -> last[0] = b);

        assertTrue(last[0].files().containsKey("unchanged.jpg"));
        assertFalse(last[0].files().containsKey("gone.jpg"));
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

    private static String md5Hex(String content) {
        return HexFormat.of().formatHex(md5Of(content));
    }

    private static byte[] md5Of(String content) {
        try {
            return java.security.MessageDigest.getInstance("MD5").digest(content.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class FakeTrash implements Trash {
        final List<Path> trashed = new ArrayList<>();

        @Override public void moveToTrash(Path file) {
            trashed.add(file);
            try {
                Files.deleteIfExists(file);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
    }

    private static final class FakeStore implements ObjectStore {
        final Map<String, ObjectInfo> remote = new HashMap<>();
        final List<String> putKeys = new ArrayList<>();
        final List<String> deletedKeys = new ArrayList<>();
        final Map<String, String> contents = new HashMap<>();
        final List<String> getKeys = new ArrayList<>();

        @Override public Map<String, ObjectInfo> list(String keyPrefix) {
            assertEquals(PREFIX, keyPrefix);
            return remote;
        }

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

        @Override public void delete(String key) {
            deletedKeys.add(key);
            remote.remove(key);
        }
    }
}
