package net.timafe.triptale.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDirTest {

    @TempDir
    Path tempDir;

    private final Path dataDir = Path.of("/data");

    @Test
    void purgeCacheEmptiesTheFolderButKeepsIt() throws IOException {
        Path tiles = LocalDir.cache(tempDir, "tiles");
        Files.createDirectories(tiles.resolve("osm/5/10"));
        Files.writeString(tiles.resolve("osm/5/10/3.png"), "x");
        Files.writeString(tiles.resolve("osm/5/10/4.png"), "y");
        Path exif = LocalDir.cache(tempDir, "exif");
        Files.createDirectories(exif);
        Files.writeString(exif.resolve("a.properties"), "z");

        assertEquals(2, LocalDir.purgeCache(tempDir, "tiles"));

        assertTrue(Files.isDirectory(tiles));
        try (var children = Files.list(tiles)) {
            assertEquals(0, children.count());
        }
        assertTrue(Files.exists(exif.resolve("a.properties")));
        assertEquals(0, LocalDir.purgeCache(tempDir, "missing"));
    }

    @Test
    void layoutLivesUnderOneFolder() {
        assertEquals(Path.of("/data/.triptale/state.yml"), LocalDir.stateFile(dataDir));
        assertEquals(Path.of("/data/.triptale/sync/attachments.yml"), LocalDir.syncBase(dataDir, "attachments"));
        assertEquals(Path.of("/data/.triptale/cache/tiles"), LocalDir.cache(dataDir, "tiles"));
    }
}
