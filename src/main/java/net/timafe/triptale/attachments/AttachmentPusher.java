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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pushes the local {@code attachments/} tree to an {@link ObjectStore} (todo 33a): uploads files
 * that are missing remotely or differ in size or MD5; never downloads or deletes. Hidden files
 * and folders ({@code .gitignore}, {@code .DS_Store}, …) are skipped.
 */
public final class AttachmentPusher {

    public record Result(int uploaded, int unchanged) {}

    /**
     * What a push would do: {@code toUpload} files ({@code uploadBytes} in total), and
     * {@code remoteOnly} objects in the store with no local file (nothing is downloaded yet).
     */
    public record Plan(int toUpload, long uploadBytes, int remoteOnly) {
        public boolean upToDate() { return toUpload == 0; }
    }

    /** Called before each file; {@code done} files of {@code total} are finished. */
    @FunctionalInterface
    public interface Progress {
        void update(int done, int total, String relativePath);
    }

    private final ObjectStore store;

    public AttachmentPusher(ObjectStore store) {
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

    /** Same comparison as {@link #push}, without uploading: one LIST plus local MD5s. */
    public Plan plan(Path attachmentsRoot, String keyPrefix) {
        Map<String, ObjectStore.ObjectInfo> remote = store.list(keyPrefix);
        Set<String> remoteOnly = new HashSet<>(remote.keySet());
        int toUpload = 0;
        long bytes = 0;
        for (Path file : localFiles(attachmentsRoot)) {
            String key = keyPrefix + relativeKey(attachmentsRoot, file);
            remoteOnly.remove(key);
            ObjectStore.ObjectInfo existing = remote.get(key);
            long size = size(file);
            boolean changed = existing == null || existing.size() != size
                    || !existing.etag().equalsIgnoreCase(HexFormat.of().formatHex(md5(file)));
            if (changed) {
                toUpload++;
                bytes += size;
            }
        }
        return new Plan(toUpload, bytes, remoteOnly.size());
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
