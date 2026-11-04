package net.timafe.triptale.attachments;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileKindTest {

    @Test
    void of_mapsByExtensionCaseInsensitive() {
        assertEquals(FileKind.IMAGE, FileKind.of("IMG_0001.HEIC"));
        assertEquals(FileKind.IMAGE, FileKind.of("tripmap.png"));
        assertEquals(FileKind.TRACK, FileKind.of("ride.gpx"));
        assertEquals(FileKind.TEXT, FileKind.of("notes.md"));
        assertEquals(FileKind.PDF, FileKind.of("ticket.PDF"));
        assertEquals(FileKind.VIDEO, FileKind.of("clip.mov"));
    }

    @Test
    void of_fallsBackToOther() {
        assertEquals(FileKind.OTHER, FileKind.of("archive.zip"));
        assertEquals(FileKind.OTHER, FileKind.of("README"));
        assertEquals(FileKind.OTHER, FileKind.of("trailing."));
        assertEquals(FileKind.OTHER, FileKind.of(null));
    }

    @Test
    void display_combinesEmojiAndLabel() {
        assertEquals("🗺 Track", FileKind.TRACK.display());
    }
}
