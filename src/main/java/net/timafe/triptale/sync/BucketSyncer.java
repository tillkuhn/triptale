package net.timafe.triptale.sync;

import net.timafe.triptale.config.BucketUrl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Syncs a local folder tree with an {@link ObjectStore} under a key prefix, as a three-way merge
 * against a {@link SyncBase} (todo 33c, see {@code docs/adr/0002-attachment-sync.md}) — used for
 * {@code attachments/} (todos 33a, 33b, 33c) and {@code radio/} (todo 52).
 * <p>
 * Without a base (missing, unreadable, or a different bucket/prefix — {@link SyncBase#isEmpty()})
 * this is simply "copy both ways, never delete": new files are downloaded or uploaded, and a
 * file that differs on both sides is uploaded (local wins). With a base, a file absent on one
 * side but present and unchanged in the base is a delete that propagates to the other side; a
 * file edited on one side and deleted on the other keeps the edit. See {@link #plan} for the
 * full classification.
 * <p>
 * Hidden files and folders ({@code .gitignore}, {@code .DS_Store}, …) are skipped in both
 * directions.
 */
public final class BucketSyncer {

    /** What should happen to one relative path. {@code NOOP} still (re)records its MD5 in the base. */
    public enum Action { DOWNLOAD, UPLOAD, DELETE_LOCAL, DELETE_REMOTE, DROP_BASE, NOOP }

    /** A single classified file: its action, and the MD5/size to transfer or record (null for drops). */
    public record FileAction(String relativePath, Action action, long size, String md5Hex) {}

    /** Above this many total deletes, or this fraction of the tree, a sync should ask first. */
    public static final int DELETE_THRESHOLD_COUNT = 20;
    public static final double DELETE_THRESHOLD_FRACTION = 0.25;

    /** The full classification of a comparison; {@link #actions} includes {@code NOOP}/{@code DROP_BASE} rows. */
    public record Plan(List<FileAction> actions) {

        public List<FileAction> of(Action action) {
            return actions.stream().filter(a -> a.action() == action).toList();
        }

        public int toDownload() { return of(Action.DOWNLOAD).size(); }
        public long downloadBytes() { return of(Action.DOWNLOAD).stream().mapToLong(FileAction::size).sum(); }
        public int toUpload() { return of(Action.UPLOAD).size(); }
        public long uploadBytes() { return of(Action.UPLOAD).stream().mapToLong(FileAction::size).sum(); }
        public int deleteLocal() { return of(Action.DELETE_LOCAL).size(); }
        public int deleteRemote() { return of(Action.DELETE_REMOTE).size(); }
        public int totalDeletes() { return deleteLocal() + deleteRemote(); }

        /** Files that exist on at least one side right now — the denominator for the mass-delete fraction. */
        public int treeSize() {
            return (int) actions.stream().filter(a -> a.action() != Action.DROP_BASE).count();
        }

        public boolean upToDate() { return toDownload() == 0 && toUpload() == 0 && totalDeletes() == 0; }

        /** True when the planned deletes are large enough that a sync should ask for confirmation first. */
        public boolean exceedsDeleteThreshold() {
            int deletes = totalDeletes();
            if (deletes == 0) return false;
            if (deletes > DELETE_THRESHOLD_COUNT) return true;
            int tree = treeSize();
            return tree > 0 && deletes > tree * DELETE_THRESHOLD_FRACTION;
        }
    }

    /** What a finished run did. */
    public record Result(int downloaded, int uploaded, int deletedLocal, int deletedRemote) {
        public int totalDeleted() { return deletedLocal() + deletedRemote(); }
    }

    /** Called before each transferred/deleted file; {@code done} of {@code total} are finished. */
    @FunctionalInterface
    public interface Progress {
        void update(int done, int total, String relativePath);
    }

    private final ObjectStore store;

    public BucketSyncer(ObjectStore store) {
        this.store = store;
    }

    /**
     * Object key prefix for a top-level data dir folder: {@code [<url prefix>/]<dirName>/}, so
     * keys mirror the paths inside the data dir.
     */
    public static String keyPrefix(BucketUrl url, String dirName) {
        return (url.prefix().isEmpty() ? "" : url.prefix() + "/") + dirName + "/";
    }

    /**
     * Compares the local tree, the bucket, and {@code base} (one LIST plus local MD5s) and
     * classifies every relative path per the three-way merge table in ADR-0002. Nothing is
     * transferred.
     */
    public Plan plan(Path root, String keyPrefix, SyncBase base) {
        Map<String, ObjectStore.ObjectInfo> remote = store.list(keyPrefix);
        Map<String, Path> localByRelative = new TreeMap<>();
        for (Path file : localFiles(root)) localByRelative.put(relativeKey(root, file), file);
        Map<String, ObjectStore.ObjectInfo> remoteByRelative = new TreeMap<>();
        remote.forEach((key, info) -> localTarget(root, keyPrefix, key)
                .ifPresent(target -> remoteByRelative.put(relativeKey(root, target), info)));

        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(localByRelative.keySet());
        paths.addAll(remoteByRelative.keySet());
        paths.addAll(base.files().keySet());

        List<FileAction> actions = new ArrayList<>();
        for (String path : paths) {
            Path localFile = localByRelative.get(path);
            ObjectStore.ObjectInfo remoteInfo = remoteByRelative.get(path);
            String baseMd5 = base.files().get(path);
            actions.add(classify(path, localFile, remoteInfo, baseMd5));
        }
        return new Plan(actions);
    }

    /** One path's action, per the three-way merge table in ADR-0002. */
    private static FileAction classify(String path, Path localFile, ObjectStore.ObjectInfo remoteInfo, String baseMd5) {
        boolean inBase = baseMd5 != null;
        String localMd5 = localFile == null ? null : HexFormat.of().formatHex(md5(localFile));
        long size = localFile != null ? size(localFile) : remoteInfo != null ? remoteInfo.size() : 0;

        if (!inBase) {
            if (localFile != null && remoteInfo == null) return new FileAction(path, Action.UPLOAD, size, localMd5);
            if (localFile == null && remoteInfo != null) {
                return new FileAction(path, Action.DOWNLOAD, remoteInfo.size(), remoteInfo.etag());
            }
            // both present: same content needs no transfer, differing content means local wins
            boolean same = matches(remoteInfo, localMd5, size);
            return same ? new FileAction(path, Action.NOOP, size, localMd5)
                    : new FileAction(path, Action.UPLOAD, size, localMd5);
        }

        boolean localDeleted = localFile == null;
        boolean localChanged = !localDeleted && !localMd5.equals(baseMd5);
        boolean remoteDeleted = remoteInfo == null;
        boolean remoteChanged = !remoteDeleted && !matches(remoteInfo, baseMd5, remoteInfo.size());

        if (localDeleted && remoteDeleted) return new FileAction(path, Action.DROP_BASE, 0, null);
        if (localDeleted && !remoteChanged) return new FileAction(path, Action.DELETE_REMOTE, remoteInfo.size(), null);
        // edit beats delete
        if (localDeleted) return new FileAction(path, Action.DOWNLOAD, remoteInfo.size(), remoteInfo.etag());
        if (remoteDeleted && !localChanged) return new FileAction(path, Action.DELETE_LOCAL, size, null);
        if (remoteDeleted) return new FileAction(path, Action.UPLOAD, size, localMd5); // edit beats delete
        if (!localChanged && remoteChanged) {
            return new FileAction(path, Action.DOWNLOAD, remoteInfo.size(), remoteInfo.etag());
        }
        if (localChanged) return new FileAction(path, Action.UPLOAD, size, localMd5); // local wins, incl. both changed
        return new FileAction(path, Action.NOOP, size, localMd5); // unchanged both sides
    }

    /** True when a remote object's size and ETag match an MD5 hex; a multipart ETag never matches. */
    private static boolean matches(ObjectStore.ObjectInfo info, String md5Hex, long size) {
        return info.size() == size && !info.etag().contains("-") && info.etag().equalsIgnoreCase(md5Hex);
    }

    /**
     * Executes {@code plan} against {@code root}/{@code keyPrefix}: downloads, uploads, and
     * trashes local/remote deletes. {@code base} is updated and handed to {@code checkpoint}
     * after every finished file, so an interrupted run (exception partway through) leaves the
     * base reflecting exactly what finished — the rest is reclassified next time.
     */
    public Result sync(Path root, String keyPrefix, SyncBase base, Plan plan, Trash trash,
                       Progress progress, Consumer<SyncBase> checkpoint) {
        SyncBase[] current = { base };
        int downloaded = 0, uploaded = 0, deletedLocal = 0, deletedRemote = 0;
        int total = plan.toDownload() + plan.toUpload() + plan.deleteLocal() + plan.deleteRemote();
        int done = 0;
        for (FileAction action : plan.actions()) {
            Path target = root.resolve(action.relativePath());
            String key = keyPrefix + action.relativePath();
            switch (action.action()) {
                case NOOP -> current[0] = current[0].withFile(action.relativePath(), action.md5Hex());
                case DROP_BASE -> current[0] = current[0].withoutFile(action.relativePath());
                case DOWNLOAD -> {
                    progress.update(done, total, action.relativePath());
                    try {
                        Files.createDirectories(target.getParent());
                    } catch (IOException ex) {
                        throw new SyncException("Could not create " + target.getParent(), ex);
                    }
                    store.get(key, target);
                    String md5Hex = verify(target, action.md5Hex());
                    current[0] = current[0].withFile(action.relativePath(), md5Hex);
                    checkpoint.accept(current[0]);
                    downloaded++;
                    done++;
                }
                case UPLOAD -> {
                    progress.update(done, total, action.relativePath());
                    store.put(key, target, HexFormat.of().parseHex(action.md5Hex()));
                    current[0] = current[0].withFile(action.relativePath(), action.md5Hex());
                    checkpoint.accept(current[0]);
                    uploaded++;
                    done++;
                }
                case DELETE_LOCAL -> {
                    progress.update(done, total, action.relativePath());
                    trash.moveToTrash(target);
                    current[0] = current[0].withoutFile(action.relativePath());
                    checkpoint.accept(current[0]);
                    deletedLocal++;
                    done++;
                }
                case DELETE_REMOTE -> {
                    progress.update(done, total, action.relativePath());
                    store.delete(key);
                    current[0] = current[0].withoutFile(action.relativePath());
                    checkpoint.accept(current[0]);
                    deletedRemote++;
                    done++;
                }
            }
        }
        checkpoint.accept(current[0]);
        return new Result(downloaded, uploaded, deletedLocal, deletedRemote);
    }

    /**
     * Where an object would live locally, or empty for keys we must not write: outside the
     * prefix, folder markers ({@code …/}), hidden segments, or anything resolving outside the
     * root ({@code ..}).
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

    /** Checks a download's MD5 against the expected ETag and returns it; a multipart ETag is skipped. */
    private static String verify(Path file, String expectedEtag) {
        String actual = HexFormat.of().formatHex(md5(file));
        if (expectedEtag == null || expectedEtag.contains("-") || actual.equalsIgnoreCase(expectedEtag)) return actual;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // the failure below is what matters
        }
        throw new SyncException("Downloaded " + file.getFileName() + " is corrupt (MD5 " + actual
                + ", expected " + expectedEtag + ")");
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
            throw new SyncException("Could not list " + root, e);
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
            throw new SyncException("Could not read " + file, e);
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
            throw new SyncException("Could not read " + file, e);
        }
        return digest.digest();
    }
}
