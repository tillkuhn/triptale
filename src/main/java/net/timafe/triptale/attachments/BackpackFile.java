package net.timafe.triptale.attachments;

import java.nio.file.Path;
import java.time.Instant;

/**
 * One file in a day's backpack folder, as listed by {@link AttachmentsDir#dayFiles} (todo 56).
 *
 * @param path     absolute path of the file
 * @param size     size in bytes
 * @param modified last-modified time
 */
public record BackpackFile(Path path, long size, Instant modified) {

    public String name() {
        return path.getFileName().toString();
    }

    public FileKind kind() {
        return FileKind.of(name());
    }
}
