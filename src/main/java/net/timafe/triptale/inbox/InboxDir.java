package net.timafe.triptale.inbox;

import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.StorageException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The data dir's {@code inbox/} folder (todo 68): one flat folder of timestamped {@code .txt}
 * files, each one captured message waiting to be attached to an entry or discarded. Always
 * local — unlike {@code attachments/}, there is no sync mode choice; the folder is gitignored
 * unconditionally, same spirit as {@code .state.yml}. No JavaFX imports (package boundary rule).
 */
@Component
public class InboxDir {

    public static final String DIR_NAME = "inbox";
    private static final String PROCESSED_DIR = "processed";
    private static final String DISCARDED_DIR = "discarded";
    private static final String GITIGNORE = """
            # Managed by TripTale: mobile inbox capture is always local, never synced
            *
            !.gitignore
            """;

    private final MarkdownStore store;

    public InboxDir(MarkdownStore store) {
        this.store = store;
    }

    public Path root() {
        return store.dataDir().resolve(DIR_NAME);
    }

    /** Writes one message as a timestamped file (created on demand); a same-second collision gets a numeric suffix. */
    public Path writeItem(LocalDateTime timestamp, String text) {
        ensureGitignore();
        Path dir = root();
        String stem = InboxItem.FILE_STAMP.format(timestamp);
        Path file = dir.resolve(stem + ".txt");
        int suffix = 1;
        while (Files.exists(file)) {
            file = dir.resolve(stem + "-" + (suffix++) + ".txt");
        }
        try {
            Files.writeString(file, text);
            return file;
        } catch (IOException e) {
            throw new StorageException("Could not write inbox item to " + file, e);
        }
    }

    /** Pending items (not yet processed/discarded), oldest first. */
    public List<InboxItem> listPending() {
        Path dir = root();
        if (!Files.isDirectory(dir)) return List.of();
        List<InboxItem> items = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path file : stream) {
                InboxItem.parse(file).ifPresent(items::add);
            }
        } catch (IOException e) {
            throw new StorageException("Could not list " + dir, e);
        }
        items.sort(Comparator.comparing(InboxItem::timestamp)
                .thenComparing(i -> i.file().getFileName().toString()));
        return items;
    }

    /** Moves an attached item out of the way so it isn't offered again. */
    public void markProcessed(Path file) {
        move(file, root().resolve(PROCESSED_DIR));
    }

    /** Moves a dismissed item out of the way so it isn't offered again. */
    public void markDiscarded(Path file) {
        move(file, root().resolve(DISCARDED_DIR));
    }

    private void move(Path file, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            Files.move(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Could not move " + file + " to " + targetDir, e);
        }
    }

    private void ensureGitignore() {
        Path gitignore = root().resolve(".gitignore");
        if (Files.exists(gitignore)) return;
        try {
            Files.createDirectories(root());
            Files.writeString(gitignore, GITIGNORE);
        } catch (IOException e) {
            throw new StorageException("Could not write " + gitignore, e);
        }
    }
}
