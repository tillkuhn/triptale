package net.timafe.triptale.sync;

import java.nio.file.Path;

/**
 * Moves a file out of the synced tree recoverably (todo 33c). Hidden behind an interface so
 * {@link BucketSyncer} tests use a fake instead of touching AWT; {@link DesktopTrash} is the
 * real implementation.
 */
public interface Trash {
    void moveToTrash(Path file);
}
