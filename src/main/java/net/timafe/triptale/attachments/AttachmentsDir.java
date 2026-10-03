package net.timafe.triptale.attachments;

import net.timafe.triptale.config.AppSettings.AttachmentSync;
import net.timafe.triptale.domain.TripRef;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.StorageException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The data dir's {@code attachments/} folder (todo 33a): one folder per tale entry day, named
 * like the entry file without {@code .md}, e.g.
 * {@code attachments/2026/some-trip/2026-09-26-Saturday/}. The app owns the folder's
 * {@code .gitignore}, whose content follows the {@link AttachmentSync} mode.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
@Component
public class AttachmentsDir {

    public static final String DIR_NAME = "attachments";
    /** Path of the managed .gitignore relative to the data dir (used as pending-commit label). */
    public static final String GITIGNORE_LABEL = DIR_NAME + "/.gitignore";

    static final String GITIGNORE_LOCAL = """
            # Managed by TripTale: attachments are not versioned in git (sync mode off or cloud)
            *
            !.gitignore
            """;
    static final String GITIGNORE_GIT = """
            # Managed by TripTale: attachments are versioned in git (sync mode git)
            """;

    private final MarkdownStore store;

    public AttachmentsDir(MarkdownStore store) {
        this.store = store;
    }

    public Path root() {
        return store.dataDir().resolve(DIR_NAME);
    }

    /** The day's folder, whether or not it exists yet. */
    public Path dayDir(TripRef ref, LocalDate date) {
        String entryName = store.entryFile(ref, date).getFileName().toString();
        return root().resolve(Integer.toString(ref.year())).resolve(ref.slug())
                .resolve(entryName.substring(0, entryName.length() - ".md".length()));
    }

    /** Creates the day's folder on demand and returns it. */
    public Path ensureDayDir(TripRef ref, LocalDate date) {
        Path dir = dayDir(ref, date);
        try {
            return Files.createDirectories(dir);
        } catch (IOException e) {
            throw new StorageException("Could not create " + dir, e);
        }
    }

    /**
     * Copies the files into the day's folder (created on demand); a file with the same name is
     * overwritten, like re-importing a GPX track (todo 33, D4).
     *
     * @return the copies, in the order given
     */
    public List<Path> addFiles(TripRef ref, LocalDate date, List<Path> files) {
        Path dir = ensureDayDir(ref, date);
        List<Path> copies = new ArrayList<>();
        for (Path file : files) {
            Path target = dir.resolve(file.getFileName());
            try {
                copies.add(Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES));
            } catch (IOException e) {
                throw new StorageException("Could not copy " + file + " to " + dir, e);
            }
        }
        return copies;
    }

    /**
     * Writes {@code attachments/.gitignore} for the given mode, creating the folder if needed.
     *
     * @return {@code true} if the file was created or its content changed
     */
    public boolean ensureGitignore(AttachmentSync mode) {
        Path gitignore = root().resolve(".gitignore");
        String content = mode == AttachmentSync.GIT ? GITIGNORE_GIT : GITIGNORE_LOCAL;
        try {
            if (Files.exists(gitignore) && Files.readString(gitignore).equals(content)) return false;
            Files.createDirectories(gitignore.getParent());
            Files.writeString(gitignore, content);
            return true;
        } catch (IOException e) {
            throw new StorageException("Could not write " + gitignore, e);
        }
    }
}
