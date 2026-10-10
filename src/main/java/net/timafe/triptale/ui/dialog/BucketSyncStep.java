package net.timafe.triptale.ui.dialog;

import javafx.application.Platform;
import javafx.concurrent.Task;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.BucketUrl;
import net.timafe.triptale.sync.BucketSyncer;
import net.timafe.triptale.sync.S3Client;
import net.timafe.triptale.ui.UiText;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A sync dialog step that mirrors one data dir folder with the bucket via {@link BucketSyncer}:
 * the Backpack row in Smart Sync ({@code attachments/}) and the Tracks row in Sync Tracks
 * ({@code radio/}). Owns the row's texts; the dialog decides when to {@link #prepare} and
 * {@link #run}.
 */
final class BucketSyncStep {

    final SyncRow row;
    private final Path root;
    private final String dirName;
    private BucketSyncer syncer;
    private String keyPrefix;

    BucketSyncStep(SyncRow row, Path root, String dirName) {
        this.row = row;
        this.root = root;
        this.dirName = dirName;
    }

    /** Validates the S3 config and builds the syncer; a message if the folder can't be synced. */
    Optional<String> configure(AppSettings.Attachments settings) {
        Optional<String> invalid = settings.validationError();
        if (invalid.isPresent()) return invalid;
        try {
            syncer = new BucketSyncer(S3Client.from(settings));
            keyPrefix = BucketSyncer.keyPrefix(BucketUrl.parse(settings.getBucketUrl()), dirName);
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.of(UiText.describe(e));
        }
    }

    /**
     * Compares with the bucket on a daemon thread (read-only) and shows the transfer plan.
     *
     * @param onDone runs on the FX thread once the row shows the result
     */
    void prepare(String threadName, Runnable onDone) {
        row.running("Comparing with bucket…");
        Task<BucketSyncer.Plan> plan = new Task<>() {
            @Override protected BucketSyncer.Plan call() {
                return syncer.plan(root, keyPrefix);
            }
        };
        plan.setOnSucceeded(e -> {
            BucketSyncer.Plan p = plan.getValue();
            if (p.upToDate()) {
                row.idle("Up to date");
                row.setEnabled(true, false);
            } else {
                List<String> parts = new ArrayList<>();
                if (p.toDownload() > 0) parts.add("↓ " + p.toDownload() + " to download (" + megabytes(p.downloadBytes()) + ")");
                if (p.toUpload() > 0) parts.add("↑ " + p.toUpload() + " to upload (" + megabytes(p.uploadBytes()) + ")");
                row.idle(String.join(" · ", parts));
                row.setEnabled(true, true);
            }
            onDone.run();
        });
        plan.setOnFailed(e -> {
            row.fail("Cannot list bucket: " + UiText.describe(plan.getException()));
            row.setEnabled(true, false);
            onDone.run();
        });
        Thread thread = new Thread(plan, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    /** Pull first, then push, mirroring git (todo 33b). Call on a worker thread. */
    void run() {
        fx(() -> row.running("Downloading…"));
        int downloaded;
        try {
            downloaded = syncer.pull(root, keyPrefix, (done, total, file) ->
                    fx(() -> row.running("Downloading " + (done + 1) + "/" + total + ": " + file)));
        } catch (RuntimeException e) {
            fx(() -> row.fail("Download failed: " + UiText.describe(e)));
            return;
        }
        fx(() -> row.running("Uploading…"));
        try {
            BucketSyncer.Result result = syncer.push(root, keyPrefix, (done, total, file) ->
                    fx(() -> row.running("Uploading " + (done + 1) + "/" + total + ": " + file)));
            fx(() -> row.ok(downloaded + " downloaded, " + result.uploaded() + " uploaded, "
                    + result.unchanged() + " unchanged"));
        } catch (RuntimeException e) {
            fx(() -> row.fail("Upload failed (" + downloaded + " downloaded): " + UiText.describe(e)));
        }
    }

    private static void fx(Runnable r) {
        Platform.runLater(r);
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0);
    }
}
