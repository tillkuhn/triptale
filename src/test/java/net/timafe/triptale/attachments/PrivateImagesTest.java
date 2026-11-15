package net.timafe.triptale.attachments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateImagesTest {

    @TempDir
    Path dir;

    private Path a;
    private Path b;

    @BeforeEach
    void setUp() throws IOException {
        a = Files.createFile(dir.resolve("a.jpg"));
        b = Files.createFile(dir.resolve("b.jpg"));
    }

    @Test
    void noListMeansNothingIsPrivate() {
        assertTrue(PrivateImages.load(dir).isEmpty());
        assertFalse(PrivateImages.isPrivate(a));
        assertEquals(List.of(a, b), PrivateImages.withoutPrivate(List.of(a, b)));
    }

    @Test
    void setWritesASortedPlainFileAndFiltersTheImage() throws IOException {
        PrivateImages.set(b, true);
        PrivateImages.set(a, true);
        assertEquals("a.jpg\nb.jpg\n", Files.readString(dir.resolve("private.txt")));
        assertTrue(PrivateImages.isPrivate(a));
        PrivateImages.set(a, false);
        assertFalse(PrivateImages.isPrivate(a));
        assertEquals(List.of(a), PrivateImages.withoutPrivate(List.of(a, b)));
    }

    @Test
    void emptyListDeletesTheFile() {
        PrivateImages.set(a, true);
        PrivateImages.set(a, false);
        assertFalse(Files.exists(dir.resolve("private.txt")));
    }

    @Test
    void rewriteDropsNamesOfFilesThatAreGone() throws IOException {
        PrivateImages.set(a, true);
        Files.delete(a);
        PrivateImages.set(b, true);
        assertEquals(Set.of("b.jpg"), PrivateImages.load(dir));
    }

    @Test
    void readIgnoresBlankLinesAndCommentsAndMatchesCaseSensitive() throws IOException {
        Files.writeString(dir.resolve("private.txt"), "# note\n\n  a.jpg  \nB.JPG\n");
        assertTrue(PrivateImages.isPrivate(a));
        assertFalse(PrivateImages.isPrivate(b));
    }
}
