package net.timafe.triptale.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link Trash} via {@code java.awt.Desktop.moveToTrash} — not JavaFX, so the package boundary
 * rule allows it, but kept behind the {@link Trash} interface so headless tests don't need a
 * desktop session. Falls back to a plain delete (with a log warning) where trashing isn't
 * supported (todo 33c).
 */
public final class DesktopTrash implements Trash {

    private static final Logger log = LoggerFactory.getLogger(DesktopTrash.class);

    @Override
    public void moveToTrash(Path file) {
        boolean trashed = false;
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            trashed = Desktop.getDesktop().moveToTrash(file.toFile());
        }
        if (!trashed) {
            log.warn("Could not move {} to the trash, deleting it instead", file);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new SyncException("Could not delete " + file, e);
            }
        }
    }
}
