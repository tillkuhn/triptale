package net.timafe.triptale.storage;

import java.nio.file.Path;

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
}
