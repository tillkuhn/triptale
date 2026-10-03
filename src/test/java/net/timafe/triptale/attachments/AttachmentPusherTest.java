package net.timafe.triptale.attachments;

import net.timafe.triptale.config.BucketUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentPusherTest {

    private static final String PREFIX = "attachments/";

    @TempDir
    Path root;

    private final FakeStore store = new FakeStore();
    private final AttachmentPusher pusher = new AttachmentPusher(store);

    @Test
    void keyPrefix_withAndWithoutUrlPrefix() {
        assertEquals("attachments/", AttachmentPusher.keyPrefix(BucketUrl.parse("s3://bucket")));
        assertEquals("triptale/attachments/", AttachmentPusher.keyPrefix(BucketUrl.parse("s3://bucket/triptale/")));
    }

    @Test
    void push_uploadsMissingFilesWithForwardSlashKeys() throws Exception {
        write("2026/trip/2026-09-26-Saturday/track.gpx", "<gpx/>");
        write("2026/trip/notes.txt", "hi");

        AttachmentPusher.Result result = pusher.push(root, PREFIX, (d, t, f) -> {});

        assertEquals(new AttachmentPusher.Result(2, 0), result);
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

        AttachmentPusher.Result result = pusher.push(root, PREFIX, (d, t, f) -> {});

        assertEquals(new AttachmentPusher.Result(2, 1), result);
        assertEquals(List.of("attachments/2026/trip/day/edited.jpg", "attachments/2026/trip/day/resized.jpg"),
                store.putKeys);
    }

    @Test
    void push_skipsHiddenFilesAndFolders() throws Exception {
        write(".gitignore", "*");
        write("2026/.DS_Store", "x");
        write("2026/trip/.cache/thumb.jpg", "x");
        write("2026/trip/day/photo.jpg", "x");

        pusher.push(root, PREFIX, (d, t, f) -> {});

        assertEquals(List.of("attachments/2026/trip/day/photo.jpg"), store.putKeys);
    }

    @Test
    void push_reportsProgressPerFile() throws Exception {
        write("a.txt", "a");
        write("b.txt", "b");
        List<String> calls = new ArrayList<>();

        pusher.push(root, PREFIX, (done, total, file) -> calls.add(done + "/" + total + " " + file));

        assertEquals(List.of("0/2 a.txt", "1/2 b.txt"), calls);
    }

    @Test
    void push_missingRootUploadsNothing() {
        AttachmentPusher.Result result = pusher.push(root.resolve("nope"), PREFIX, (d, t, f) -> {});
        assertEquals(new AttachmentPusher.Result(0, 0), result);
    }

    private Path write(String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private static ObjectStore.ObjectInfo info(Path file) throws Exception {
        return new ObjectStore.ObjectInfo(Files.size(file), HexFormat.of().formatHex(AttachmentPusher.md5(file)));
    }

    private static final class FakeStore implements ObjectStore {
        final Map<String, ObjectInfo> remote = new HashMap<>();
        final List<String> putKeys = new ArrayList<>();

        @Override public Map<String, ObjectInfo> list(String keyPrefix) {
            assertEquals(PREFIX, keyPrefix);
            return remote;
        }

        @Override public void put(String key, Path file, byte[] md5) {
            assertArrayEquals(AttachmentPusher.md5(file), md5);
            putKeys.add(key);
        }
    }
}
