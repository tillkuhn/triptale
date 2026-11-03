package net.timafe.triptale.storage;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalDirTest {

    private final Path dataDir = Path.of("/data");

    @Test
    void layoutLivesUnderOneFolder() {
        assertEquals(Path.of("/data/.triptale/state.yml"), LocalDir.stateFile(dataDir));
        assertEquals(Path.of("/data/.triptale/sync/attachments.yml"), LocalDir.syncBase(dataDir, "attachments"));
        assertEquals(Path.of("/data/.triptale/cache/tiles"), LocalDir.cache(dataDir, "tiles"));
    }
}
