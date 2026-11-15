package net.timafe.triptale.attachments;

import net.timafe.triptale.storage.StorageException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Backpack images that are kept but never exported (todo 67, ADR-0005): a plain-named
 * {@value #FILE_NAME} in the day folder lists their file names, one per line. Blank lines and
 * {@code #} comments are ignored, names match exactly (case-sensitive). The name has no leading
 * dot on purpose: dot files are skipped by the bucket sync.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public final class PrivateImages {

    public static final String FILE_NAME = "private.txt";

    private PrivateImages() {
    }

    /** The private file names of the day folder; empty if there is no list. */
    public static Set<String> load(Path dayDir) {
        Path file = dayDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return Set.of();
        try {
            Set<String> names = new TreeSet<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String name = line.strip();
                if (!name.isEmpty() && !name.startsWith("#")) names.add(name);
            }
            return names;
        } catch (IOException e) {
            throw new StorageException("Could not read " + file, e);
        }
    }

    public static boolean isPrivate(Path image) {
        Path dir = image.getParent();
        return dir != null && load(dir).contains(image.getFileName().toString());
    }

    /** The images without the private ones, in their order. */
    public static List<Path> withoutPrivate(List<Path> images) {
        Map<Path, Set<String>> lists = new HashMap<>();
        return images.stream()
                .filter(p -> p.getParent() == null || !lists.computeIfAbsent(p.getParent(), PrivateImages::load)
                        .contains(p.getFileName().toString()))
                .toList();
    }

    /**
     * Marks the image private or not and rewrites the list, sorted, without names of files that
     * no longer exist in the folder. An empty list deletes the file.
     */
    public static void set(Path image, boolean makePrivate) {
        Path dir = image.getParent();
        String name = image.getFileName().toString();
        Set<String> names = new TreeSet<>(load(dir));
        if (makePrivate) names.add(name); else names.remove(name);
        names.removeIf(n -> !n.equals(name) && !Files.exists(dir.resolve(n)));
        Path file = dir.resolve(FILE_NAME);
        try {
            if (names.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                Files.writeString(file, String.join("\n", names) + "\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new StorageException("Could not write " + file, e);
        }
    }
}
