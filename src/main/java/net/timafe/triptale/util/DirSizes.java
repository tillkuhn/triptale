package net.timafe.triptale.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Approximate on-disk size of a folder tree (sum of regular file sizes). No JavaFX imports. */
public final class DirSizes {

    private DirSizes() {}

    /** Total bytes of all regular files below {@code dir}; 0 if it doesn't exist. Unreadable files are skipped. */
    public static long totalBytes(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException | java.io.UncheckedIOException e) {
            return 0;
        }
    }
}
