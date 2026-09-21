package net.timafe.triptale.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Dedicated subdirectory of the OS temp dir for export previews, so a single startup
 * {@link #sweep()} can clear stale files left behind by crashed or killed sessions.
 */
@Component
public class ExportTempFiles {

    private static final Logger log = LoggerFactory.getLogger(ExportTempFiles.class);

    private static Path dir() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "triptale");
    }

    /** Creates a fresh temp file in the export temp dir, registered for deletion on clean shutdown. */
    public Path newFile(String prefix, String suffix) throws IOException {
        Files.createDirectories(dir());
        Path tmp = Files.createTempFile(dir(), prefix, suffix);
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    /** Best-effort removal of every file in the export temp dir. Call once on startup. */
    public void sweep() {
        Path dir = dir();
        if (!Files.isDirectory(dir)) return;
        try (var files = Files.list(dir)) {
            files.forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException e) {
                    log.warn("Could not delete stale temp file {}: {}", f, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean temp dir {}: {}", dir, e.getMessage());
        }
    }
}
