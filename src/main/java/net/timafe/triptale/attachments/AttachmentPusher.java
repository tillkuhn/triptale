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
import java.util.stream.Stream;

/**
 * Pushes the local {@code attachments/} tree to an {@link ObjectStore} (todo 33a): uploads files
 * that are missing remotely or differ in size or MD5; never downloads or deletes. Hidden files
 * and folders ({@code .gitignore}, {@code .DS_Store}, …) are skipped.
 */
public final class AttachmentPusher {

    public record Result(int uploaded, int unchanged) {}

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
            ObjectStore.ObjectInfo existing = remote.get(key);
            long size = size(file);
            if (existing != null && existing.size() == size) {
                byte[] md5 = md5(file);
                if (existing.etag().equalsIgnoreCase(HexFormat.of().formatHex(md5))) continue;
                store.put(key, file, md5);
            } else {
                store.put(key, file, md5(file));
            }
            uploaded++;
        }
        return new Result(uploaded, files.size() - uploaded);
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
