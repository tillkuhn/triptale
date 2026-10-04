package net.timafe.triptale.attachments;

import net.timafe.triptale.config.BucketUrl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Syncs the local {@code attachments/} tree with an {@link ObjectStore} (todos 33a, 33b), as
 * "copy both ways, never delete":
 * <ul>
 *   <li><b>pull</b> downloads objects that have no local file; it never overwrites a local file,</li>
 *   <li><b>push</b> uploads files that are missing remotely or differ in size or MD5 — so when
 *       both sides have a file with different content, the local copy wins.</li>
 * </ul>
 * Nothing is deleted on either side. Hidden files and folders ({@code .gitignore},
 * {@code .DS_Store}, …) are skipped in both directions.
 */
public final class AttachmentSyncer {

    public record Result(int uploaded, int unchanged) {}

    /**
     * What a sync would do: {@code toDownload} objects with no local file and {@code toUpload}
     * local files that are new or changed, with their total sizes.
     */
    public record Plan(int toDownload, long downloadBytes, int toUpload, long uploadBytes) {
        public boolean upToDate() { return toDownload == 0 && toUpload == 0; }
    }

    /** Called before each file; {@code done} files of {@code total} are finished. */
    @FunctionalInterface
    public interface Progress {
        void update(int done, int total, String relativePath);
    }

    private final ObjectStore store;

    public AttachmentSyncer(ObjectStore store) {
        this.store = store;
    }

    /**
     * Object key prefix for the attachments tree: {@code [<url prefix>/]attachments/}, so keys
     * mirror the paths inside the data dir.
     */
    public static String keyPrefix(BucketUrl url) {
        return (url.prefix().isEmpty() ? "" : url.prefix() + "/") + AttachmentsDir.DIR_NAME + "/";
    }

    public Result push(Path attachmentsRoot, String keyPrefix, Progress progress) {
        List<Path> files = localFiles(attachmentsRoot);
        Map<String, ObjectStore.ObjectInfo> remote = store.list(keyPrefix);
        int uploaded = 0;
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String relative = relativeKey(attachmentsRoot, file);
            progress.update(i, files.size(), relative);
            String key = keyPrefix + relative;
            byte[] md5 = changedMd5(file, remote.get(key));
            if (md5 == null) continue;
            store.put(key, file, md5);
            uploaded++;
        }
        return new Result(uploaded, files.size() - uploaded);
    }

    /**
     * Downloads every object that has no local file (creating day folders on demand) and
     * returns how many were downloaded. A download whose MD5 doesn't match the object's ETag is
     * removed again and fails the pull.
     */
    public int pull(Path attachmentsRoot, String keyPrefix, Progress progress) {
        Map<Path, ObjectStore.ObjectInfo> missing = missingLocally(attachmentsRoot, keyPrefix, store.list(keyPrefix));
        int done = 0;
        for (Map.Entry<Path, ObjectStore.ObjectInfo> e : missing.entrySet()) {
            Path target = e.getKey();
            progress.update(done, missing.size(), relativeKey(attachmentsRoot, target));
            try {
                Files.createDirectories(target.getParent());
            } catch (IOException ex) {
                throw new AttachmentException("Could not create " + target.getParent(), ex);
            }
            store.get(keyPrefix + relativeKey(attachmentsRoot, target), target);
            verify(target, e.getValue());
            done++;
        }
        return done;
    }

    /** Same comparisons as {@link #pull} and {@link #push}, without transferring: one LIST plus local MD5s. */
    public Plan plan(Path attachmentsRoot, String keyPrefix) {
        Map<String, ObjectStore.ObjectInfo> remote = store.list(keyPrefix);
        int toDownload = 0;
        long downloadBytes = 0;
        for (ObjectStore.ObjectInfo info : missingLocally(attachmentsRoot, keyPrefix, remote).values()) {
            toDownload++;
            downloadBytes += info.size();
        }
        int toUpload = 0;
        long uploadBytes = 0;
        for (Path file : localFiles(attachmentsRoot)) {
            ObjectStore.ObjectInfo existing = remote.get(keyPrefix + relativeKey(attachmentsRoot, file));
            long size = size(file);
            boolean changed = existing == null || existing.size() != size
                    || !existing.etag().equalsIgnoreCase(HexFormat.of().formatHex(md5(file)));
            if (changed) {
                toUpload++;
                uploadBytes += size;
            }
        }
        return new Plan(toDownload, downloadBytes, toUpload, uploadBytes);
    }

    /** Remote objects with no local file, by local target path (sorted). Unsafe keys are skipped. */
    private static Map<Path, ObjectStore.ObjectInfo> missingLocally(Path root, String keyPrefix,
                                                                  Map<String, ObjectStore.ObjectInfo> remote) {
        Map<Path, ObjectStore.ObjectInfo> missing = new TreeMap<>();
        remote.forEach((key, info) -> localTarget(root, keyPrefix, key)
                .filter(target -> !Files.exists(target))
                .ifPresent(target -> missing.put(target, info)));
        return missing;
    }

    /**
     * Where an object would live locally, or empty for keys we must not write: outside the
     * prefix, folder markers ({@code …/}), hidden segments, or anything resolving outside the
     * attachments root ({@code ..}).
     */
    static Optional<Path> localTarget(Path root, String keyPrefix, String key) {
        if (!key.startsWith(keyPrefix) || key.endsWith("/")) return Optional.empty();
        String relative = key.substring(keyPrefix.length());
        if (relative.isEmpty()) return Optional.empty();
        for (String segment : relative.split("/")) {
            if (segment.isEmpty() || segment.startsWith(".") || segment.contains("\\")) return Optional.empty();
        }
        Path target = root.resolve(relative).normalize();
        return target.startsWith(root.normalize()) ? Optional.of(target) : Optional.empty();
    }

    /** Checks a download against the object's ETag; multipart ETags ({@code …-N}) aren't MD5s and are skipped. */
    private static void verify(Path file, ObjectStore.ObjectInfo info) {
        if (info.etag().contains("-")) return;
        String actual = HexFormat.of().formatHex(md5(file));
        if (actual.equalsIgnoreCase(info.etag())) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // the failure below is what matters
        }
        throw new AttachmentException("Downloaded " + file.getFileName() + " is corrupt (MD5 " + actual
                + ", expected " + info.etag() + ")");
    }

    /** The file's MD5 if it is missing remotely or differs in size or MD5, else {@code null}. */
    private static byte[] changedMd5(Path file, ObjectStore.ObjectInfo existing) {
        byte[] md5 = md5(file);
        if (existing == null || existing.size() != size(file)) return md5;
        return existing.etag().equalsIgnoreCase(HexFormat.of().formatHex(md5)) ? null : md5;
    }

    /** Regular files under {@code root}, sorted, without hidden files or anything in hidden folders. */
    static List<Path> localFiles(Path root) {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> !isHidden(root.relativize(p)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new AttachmentException("Could not list " + root, e);
        }
    }

    private static boolean isHidden(Path relative) {
        for (Path segment : relative) {
            if (segment.toString().startsWith(".")) return true;
        }
        return false;
    }

    /** Relative path with {@code /} separators, regardless of the platform. */
    private static String relativeKey(Path root, Path file) {
        StringBuilder sb = new StringBuilder();
        for (Path segment : root.relativize(file)) {
            if (!sb.isEmpty()) sb.append('/');
            sb.append(segment);
        }
        return sb.toString();
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new AttachmentException("Could not read " + file, e);
        }
    }

    /** MD5 of the file, streamed so large photos don't end up in memory. */
    static byte[] md5(Path file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new AttachmentException("Could not read " + file, e);
        }
        return digest.digest();
    }
}
