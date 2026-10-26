package net.timafe.triptale.inbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxItemTest {

    @TempDir
    Path dir;

    @Test
    void parsesTimestampFromFilenameAndTextFromContent() throws IOException {
        Path file = dir.resolve("20261023-143207.txt");
        Files.writeString(file, "Saw a great waterfall today");

        var item = InboxItem.parse(file);

        assertTrue(item.isPresent());
        assertEquals(LocalDateTime.of(2026, 10, 23, 14, 32, 7), item.get().timestamp());
        assertEquals("Saw a great waterfall today", item.get().text());
    }

    @Test
    void ignoresACollisionSuffixInTheFilename() throws IOException {
        Path file = dir.resolve("20261023-143207-1.txt");
        Files.writeString(file, "Second message that second");

        var item = InboxItem.parse(file);

        assertTrue(item.isPresent());
        assertEquals(LocalDateTime.of(2026, 10, 23, 14, 32, 7), item.get().timestamp());
    }

    @Test
    void isEmptyForAnUnparseableFilename() throws IOException {
        Path file = dir.resolve("not-a-timestamp.txt");
        Files.writeString(file, "whatever");

        assertFalse(InboxItem.parse(file).isPresent());
    }

    @Test
    void picksUpASameStemJpgSidecarAsThePhoto() throws IOException {
        Path file = dir.resolve("20261023-143207.txt");
        Files.writeString(file, "On the summit");
        Files.write(dir.resolve("20261023-143207.jpg"), new byte[] {1, 2, 3});

        var item = InboxItem.parse(file);

        assertTrue(item.isPresent());
        assertTrue(item.get().photo().isPresent());
        assertEquals(dir.resolve("20261023-143207.jpg"), item.get().photo().get());
    }

    @Test
    void hasNoPhotoWhenNoSidecarExists() throws IOException {
        Path file = dir.resolve("20261023-143207.txt");
        Files.writeString(file, "Just a note");

        var item = InboxItem.parse(file);

        assertTrue(item.isPresent());
        assertFalse(item.get().photo().isPresent());
    }
}
