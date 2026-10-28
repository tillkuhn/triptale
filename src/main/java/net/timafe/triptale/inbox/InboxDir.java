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
 * The data dir's {@code inbox/} folder (todo 68, raw payload capture todo 73, photo capture
 * todo 74): one flat folder of timestamped {@code .txt} files, each one captured message waiting
 * to be attached to an entry or discarded, plus two optional same-stem sidecars — a {@code .json}
  * raw Telegram payload (read back only for its {@code message_id}/{@code from} by
 * {@link InboxItem}, todo 75 — everything else in it stays debugging/future data-extraction) and,
 for a photo message, a {@code .jpg} with the downloaded image ({@code .txt}
 * then holds the caption, possibly empty). {@code raw/} holds payloads for updates that never
 * became a pending item at all (locations, undownloadable photos, …) — same purpose, just for
 * content v1 doesn't parse yet. Always local — unlike {@code attachments/}, there is no sync mode
 * choice; the whole folder (including all subfolders) is gitignored unconditionally, same spirit
 * as {@code .state.yml}. No JavaFX imports (package boundary rule).
 */
@Component
public class InboxDir {

    public static final String DIR_NAME = "inbox";
    private static final String PROCESSED_DIR = "processed";
    private static final String DISCARDED_DIR = "discarded";
    private static final String RAW_DIR = "raw";
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

    /**
     * Writes one text message as a timestamped file (created on demand); a same-second collision
     * gets a numeric suffix. If {@code rawJson} is non-blank, it's written alongside as a
     * same-stem {@code .json} sidecar (todo 73) — the raw Telegram payload the message came from,
     * kept for debugging/future data-extraction; {@link InboxItem} reads back only
     * {@code message_id}/{@code from} from it (todo 75).
     */
    public Path writeItem(LocalDateTime timestamp, String text, String rawJson) {
        return writeItem(timestamp, text, null, rawJson);
    }

    /**
     * Writes one photo message (todo 74): {@code text} is the caption (possibly empty — not every
     * photo has one) in the timestamped {@code .txt}, {@code photo} is the downloaded image bytes
     * in a same-stem {@code .jpg} sidecar, and {@code rawJson} is the same-stem {@code .json} raw
     * payload as for a text item (todo 73). A {@code null}/empty {@code photo} behaves exactly
     * like the text-only overload.
     */
    public Path writeItem(LocalDateTime timestamp, String text, byte[] photo, String rawJson) {
        ensureGitignore();
        Path file = uniqueFile(timestamp);
        try {
            Files.writeString(file, text == null ? "" : text);
            if (rawJson != null && !rawJson.isBlank()) {
                Files.writeString(sidecar(file, ".json"), rawJson);
            }
            if (photo != null && photo.length > 0) {
                Files.write(sidecar(file, ".jpg"), photo);
            }
            return file;
        } catch (IOException e) {
            throw new StorageException("Could not write inbox item to " + file, e);
        }
    }

    private Path uniqueFile(LocalDateTime timestamp) {
        Path dir = root();
        String stem = InboxItem.FILE_STAMP.format(timestamp);
        Path file = dir.resolve(stem + ".txt");
        int suffix = 1;
        while (Files.exists(file)) {
            file = dir.resolve(stem + "-" + (suffix++) + ".txt");
        }
        return file;
    }

    /**
     * Raw payload for an update that never became a pending item (todo 73) — non-text content
     * (photos, locations, …) from the configured chat that v1 doesn't parse yet. Written into
     * {@code inbox/raw/}, named by timestamp + Telegram's {@code update_id} (several such updates
     * can share a second), never surfaced in {@link #listPending()} or the Inbox dialog. Kept
     * only so those fast-follows can be designed against real samples instead of guesswork.
     */
    public void writeRawCapture(LocalDateTime timestamp, long updateId, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return;
        Path dir = root().resolve(RAW_DIR);
        Path file = dir.resolve(InboxItem.FILE_STAMP.format(timestamp) + "-" + updateId + ".json");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, rawJson);
        } catch (IOException e) {
            throw new StorageException("Could not write raw inbox capture to " + file, e);
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
            for (String ext : new String[] {".json", ".jpg"}) {
                Path sidecar = sidecar(file, ext);
                if (Files.exists(sidecar)) {
                    Files.move(sidecar, targetDir.resolve(sidecar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new StorageException("Could not move " + file + " to " + targetDir, e);
        }
    }

    /**
     * The same-stem sidecar for a {@code .txt} inbox item, if any — {@code .json} for the raw
     * payload (todo 73) or {@code .jpg} for a downloaded photo (todo 74).
     */
    private Path sidecar(Path txtFile, String extension) {
        String name = txtFile.getFileName().toString().replaceFirst("\\.txt$", extension);
        return txtFile.resolveSibling(name);
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
