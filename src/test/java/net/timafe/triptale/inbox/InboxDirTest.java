package net.timafe.triptale.inbox;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InboxDirTest {

    @TempDir
    Path tempDir;

    private Path dataDir;
    private InboxDir inboxDir;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("settings").toString());
        SettingsStore settingsStore = new SettingsStore(props);
        dataDir = tempDir.resolve("data");
        AppSettings settings = new AppSettings();
        settings.setDataDir(dataDir.toString());
        settingsStore.save(settings);
        inboxDir = new InboxDir(new MarkdownStore(settingsStore));
    }

    @Test
    void writeItem_textOnly_writesTxtAndJsonSidecarNoPhoto() throws Exception {
        LocalDateTime ts = LocalDateTime.of(2026, 10, 23, 14, 32, 7);

        Path file = inboxDir.writeItem(ts, "Saw a great waterfall", "{\"raw\":true}");

        assertEquals(dataDir.resolve("inbox/20261023-143207.txt"), file);
        assertEquals("Saw a great waterfall", Files.readString(file));
        assertEquals("{\"raw\":true}", Files.readString(dataDir.resolve("inbox/20261023-143207.json")));
        assertFalse(Files.exists(dataDir.resolve("inbox/20261023-143207.jpg")));

        var item = inboxDir.listPending();
        assertEquals(1, item.size());
        assertTrue(item.get(0).photo().isEmpty());
    }

    @Test
    void writeItem_withPhoto_writesJpgSidecarAndSurfacesInListPending() throws Exception {
        LocalDateTime ts = LocalDateTime.of(2026, 10, 23, 14, 32, 7);
        byte[] photoBytes = {1, 2, 3, 4};

        Path file = inboxDir.writeItem(ts, "On the summit", photoBytes, "{\"raw\":true}");

        assertEquals("On the summit", Files.readString(file));
        Path photoPath = dataDir.resolve("inbox/20261023-143207.jpg");
        assertArrayEquals(photoBytes, Files.readAllBytes(photoPath));

        var items = inboxDir.listPending();
        assertEquals(1, items.size());
        assertTrue(items.get(0).photo().isPresent());
        assertEquals(photoPath, items.get(0).photo().get());
    }

    @Test
    void writeItem_withPhotoAndNoCaption_stillListedWithEmptyText() {
        LocalDateTime ts = LocalDateTime.of(2026, 10, 23, 14, 32, 7);

        inboxDir.writeItem(ts, "", new byte[] {9}, null);

        var items = inboxDir.listPending();
        assertEquals(1, items.size());
        assertEquals("", items.get(0).text());
        assertTrue(items.get(0).photo().isPresent());
    }

    @Test
    void markProcessed_movesTxtJsonAndJpgTogether() {
        LocalDateTime ts = LocalDateTime.of(2026, 10, 23, 14, 32, 7);
        Path file = inboxDir.writeItem(ts, "caption", new byte[] {1}, "{}");

        inboxDir.markProcessed(file);

        Path processedDir = dataDir.resolve("inbox/processed");
        assertTrue(Files.exists(processedDir.resolve("20261023-143207.txt")));
        assertTrue(Files.exists(processedDir.resolve("20261023-143207.json")));
        assertTrue(Files.exists(processedDir.resolve("20261023-143207.jpg")));
        assertTrue(inboxDir.listPending().isEmpty());
    }

    @Test
    void markDiscarded_movesTxtAndJpgTogether() {
        LocalDateTime ts = LocalDateTime.of(2026, 10, 23, 14, 32, 7);
        Path file = inboxDir.writeItem(ts, "", new byte[] {1, 2}, null);

        inboxDir.markDiscarded(file);

        Path discardedDir = dataDir.resolve("inbox/discarded");
        assertTrue(Files.exists(discardedDir.resolve("20261023-143207.txt")));
        assertTrue(Files.exists(discardedDir.resolve("20261023-143207.jpg")));
        assertTrue(inboxDir.listPending().isEmpty());
    }

    @Test
    void writeRawCapture_writesIntoRawSubfolderNamedByUpdateId() throws Exception {
        LocalDateTime ts = LocalDateTime.of(2026, 10, 23, 14, 32, 7);

        inboxDir.writeRawCapture(ts, 42L, "{\"update_id\":42}");

        Path raw = dataDir.resolve("inbox/raw/20261023-143207-42.json");
        assertEquals("{\"update_id\":42}", Files.readString(raw));
        assertTrue(inboxDir.listPending().isEmpty(), "raw captures never become pending items");
    }

    @Test
    void writeItem_collisionGetsNumericSuffixAndSidecarsFollowSameStem() throws Exception {
        LocalDateTime ts = LocalDateTime.of(2026, 10, 23, 14, 32, 7);
        inboxDir.writeItem(ts, "first", null);

        Path second = inboxDir.writeItem(ts, "second", new byte[] {7}, null);

        assertEquals(dataDir.resolve("inbox/20261023-143207-1.txt"), second);
        assertTrue(Files.exists(dataDir.resolve("inbox/20261023-143207-1.jpg")));
        assertEquals(2, inboxDir.listPending().size());
    }
}
