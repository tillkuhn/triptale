package net.timafe.triptale.radio;

import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.StorageException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

/**
 * The data dir's {@code radio/} folder (todo 46): a reusable pool of the user's favourite
 * tracks, independent of any trip. Like {@code attachments/}, its content is never versioned in
 * git; the app owns {@code radio/.gitignore}.
 *
 * <p>No JavaFX imports (package boundary rule) — playback lives in {@code ui.RadioPlayer}.
 */
@Component
public class RadioLibrary {

    public static final String DIR_NAME = "radio";
    /** Path of the managed .gitignore relative to the data dir (used as pending-commit label). */
    public static final String GITIGNORE_LABEL = DIR_NAME + "/.gitignore";

    /** Formats JavaFX Media plays on every platform (no FLAC/OGG). */
    static final Set<String> EXTENSIONS = Set.of("mp3", "m4a", "aac", "wav", "aif", "aiff");

    static final String GITIGNORE = """
            # Managed by TripTale: radio tracks are not versioned in git
            *
            !.gitignore
            """;

    private final MarkdownStore store;

    public RadioLibrary(MarkdownStore store) {
        this.store = store;
    }

    public Path root() {
        return store.dataDir().resolve(DIR_NAME);
    }

    /** All playable tracks below {@code radio/} (recursive), sorted by relative path; empty if the folder is missing. */
    public List<Track> listTracks() {
        Path root = root();
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(RadioLibrary::isPlayable)
                    .map(f -> new Track(f, root.relativize(f).toString().replace('\\', '/')))
                    .sorted(Comparator.comparing(Track::relative, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException e) {
            throw new StorageException("Could not list " + root, e);
        }
    }

    /**
     * A random track, avoiding {@code previous} (so "next" never repeats the current song) unless
     * it is the only one.
     */
    public Optional<Track> random(RandomGenerator rnd, Track previous) {
        List<Track> tracks = listTracks();
        if (tracks.isEmpty()) return Optional.empty();
        List<Track> candidates = tracks.size() > 1 && previous != null
                ? tracks.stream().filter(t -> !t.file().equals(previous.file())).toList()
                : tracks;
        return Optional.of(candidates.get(rnd.nextInt(candidates.size())));
    }

    /**
     * Writes the app-managed {@code radio/.gitignore}, creating the folder if needed.
     *
     * @return true if the file was created or changed
     */
    public boolean ensureManagedFiles() {
        Path file = root().resolve(".gitignore");
        try {
            if (Files.exists(file) && Files.readString(file).equals(GITIGNORE)) return false;
            Files.createDirectories(file.getParent());
            Files.writeString(file, GITIGNORE);
            return true;
        } catch (IOException e) {
            throw new StorageException("Could not write " + file, e);
        }
    }

    static boolean isPlayable(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && !name.startsWith(".")
                && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
