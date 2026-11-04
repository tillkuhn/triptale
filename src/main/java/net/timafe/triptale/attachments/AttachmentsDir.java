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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The data dir's {@code attachments/} folder (todo 33a): one folder per tale entry day, named
 * like the entry file without {@code .md}, e.g.
 * {@code attachments/2026/some-trip/2026-09-26-Saturday/}. The app owns the folder's
 * {@code .gitignore}, whose content follows the {@link AttachmentSync} mode, and its
 * {@code .gitattributes}, which marks every attachment binary.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
@Component
public class AttachmentsDir {

    public static final String DIR_NAME = "attachments";
    /** File name of a day's saved Sgt. Mapper map, see {@link #tripMapFile}. */
    public static final String TRIP_MAP_FILE = "tripmap.png";
    /** Path of the managed .gitignore relative to the data dir (used as pending-commit label). */
    public static final String GITIGNORE_LABEL = DIR_NAME + "/.gitignore";
    /** Path of the managed .gitattributes relative to the data dir (used as pending-commit label). */
    public static final String GITATTRIBUTES_LABEL = DIR_NAME + "/.gitattributes";

    static final String GITIGNORE_LOCAL = """
            # Managed by TripTale: attachments are not versioned in git (sync mode off or cloud)
            *
            !.gitignore
            !.gitattributes
            """;
    static final String GITIGNORE_GIT = """
            # Managed by TripTale: attachments are versioned in git (sync mode git)
            """;
    /**
     * Same in every mode: treat all attachments as binary, so git never converts line endings
     * (e.g. {@code core.autocrlf} on Windows turning a GPX file's LF into CRLF). Keeps the bytes,
     * and with them the MD5 that attachment push compares, identical on every OS.
     */
    static final String GITATTRIBUTES = """
            # Managed by TripTale: attachments are binary, no line-ending conversion on any OS
            * -text
            """;

    private final MarkdownStore store;

    public AttachmentsDir(MarkdownStore store) {
        this.store = store;
    }

    public Path root() {
        return store.dataDir().resolve(DIR_NAME);
    }

    /** The trip's own folder (above the per-day folders), whether or not it exists yet. */
    public Path tripDir(TripRef ref) {
        return root().resolve(Integer.toString(ref.year())).resolve(ref.slug());
    }

    /** The day's folder, whether or not it exists yet. */
    public Path dayDir(TripRef ref, LocalDate date) {
        String entryName = store.entryFile(ref, date).getFileName().toString();
        return tripDir(ref).resolve(entryName.substring(0, entryName.length() - ".md".length()));
    }

    /**
     * The day's saved Sgt. Mapper map (todo 82): one fixed name per entry, so there is at most
     * one map per day and it shows up in the day's gallery like any other backpack image.
     */
    public Path tripMapFile(TripRef ref, LocalDate date) {
        return dayDir(ref, date).resolve(TRIP_MAP_FILE);
    }

    /** Copies {@code map} (a temp render) to the day's {@link #tripMapFile}, replacing any earlier one. */
    public Path saveTripMap(TripRef ref, LocalDate date, Path map) {
        Path target = tripMapFile(ref, date);
        try {
            Files.createDirectories(target.getParent());
            return Files.copy(map, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Could not save map to " + target, e);
        }
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
     * The day's backpack (todo 56): regular, non-hidden files directly in the day folder, sorted
     * by name (case-insensitive). Hidden files ({@code .DS_Store}, a pull's {@code .part}
     * leftovers) and subfolders are skipped — a backpack is flat. Empty if the folder doesn't
     * exist yet.
     */
    public List<BackpackFile> dayFiles(TripRef ref, LocalDate date) {
        Path dir = dayDir(ref, date);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            List<BackpackFile> result = new ArrayList<>();
            for (Path p : files.toList()) {
                if (p.getFileName().toString().startsWith(".") || !Files.isRegularFile(p)) continue;
                result.add(new BackpackFile(p, Files.size(p), Files.getLastModifiedTime(p).toInstant()));
            }
            result.sort(Comparator.comparing(BackpackFile::name, String.CASE_INSENSITIVE_ORDER));
            return result;
        } catch (IOException e) {
            throw new StorageException("Could not list " + dir, e);
        }
    }

    /**
     * Deletes one file from the day's backpack. Only the local file goes; cloud sync propagates
     * the delete through its sync base (todo 33c), git mode sees an ordinary deletion. Refuses
     * anything that isn't a file directly in the day folder.
     */
    public void deleteFile(TripRef ref, LocalDate date, String fileName) {
        Path dir = dayDir(ref, date);
        Path file = dir.resolve(fileName).normalize();
        if (!dir.equals(file.getParent()) || !Files.isRegularFile(file)) {
            throw new StorageException("Not a backpack file of " + date + ": " + fileName);
        }
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new StorageException("Could not delete " + file, e);
        }
    }

    /**
     * Writes the app-managed {@code attachments/.gitignore} (content per mode) and
     * {@code attachments/.gitattributes}, creating the folder if needed.
     *
     * @return the labels ({@link #GITIGNORE_LABEL}, {@link #GITATTRIBUTES_LABEL}) of the files that
     *         were created or changed; empty if both were already up to date
     */
    public List<String> ensureManagedFiles(AttachmentSync mode) {
        List<String> changed = new ArrayList<>();
        if (ensureFile(".gitignore", mode == AttachmentSync.GIT ? GITIGNORE_GIT : GITIGNORE_LOCAL)) {
            changed.add(GITIGNORE_LABEL);
        }
        if (ensureFile(".gitattributes", GITATTRIBUTES)) changed.add(GITATTRIBUTES_LABEL);
        return changed;
    }

    private boolean ensureFile(String name, String content) {
        Path file = root().resolve(name);
        try {
            if (Files.exists(file) && Files.readString(file).equals(content)) return false;
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            return true;
        } catch (IOException e) {
            throw new StorageException("Could not write " + file, e);
        }
    }
}
