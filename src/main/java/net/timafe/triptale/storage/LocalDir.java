package net.timafe.triptale.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Layout of {@code <data-dir>/.triptale/}, the single gitignored folder for everything that is
 * local to one device (todo 83):
 * <pre>
 * .triptale/
 * ├── state.yml            # app bookkeeping (last trip/entry, ...)
 * ├── sync/&lt;dir&gt;.yml       # per-folder bucket sync bases (todo 33c)
 * └── cache/&lt;name&gt;/        # disposable caches (tiles, exif), safe to delete
 * </pre>
 * Only {@code cache/} may be wiped freely; {@code state.yml} and {@code sync/} are state.
 */
public final class LocalDir {

    /** Folder name at the data-dir root, also the single {@code .gitignore} entry. */
    public static final String NAME = ".triptale";

    private LocalDir() {
    }

    public static Path root(Path dataDir) {
        return dataDir.resolve(NAME);
    }

    public static Path stateFile(Path dataDir) {
        return root(dataDir).resolve("state.yml");
    }

    /** Sync base for the bucket-synced folder {@code dirName} (e.g. {@code attachments}). */
    public static Path syncBase(Path dataDir, String dirName) {
        return root(dataDir).resolve("sync").resolve(dirName + ".yml");
    }

    /** Cache folder {@code name} (e.g. {@code tiles}, {@code exif}); contents are disposable. */
    public static Path cache(Path dataDir, String name) {
        return root(dataDir).resolve("cache").resolve(name);
    }

    /**
     * Deletes everything inside cache folder {@code name} (the folder itself stays).
     *
     * @return the number of files deleted
     */
    public static int purgeCache(Path dataDir, String name) {
        Path dir = cache(dataDir, name);
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            int files = 0;
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (p.equals(dir)) continue;
                boolean file = Files.isRegularFile(p);
                Files.delete(p);
                if (file) files++;
            }
            return files;
        } catch (IOException e) {
            throw new StorageException("Failed to purge cache '" + name + "': " + e.getMessage(), e);
        }
    }
}
