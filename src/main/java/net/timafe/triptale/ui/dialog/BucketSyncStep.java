package net.timafe.triptale.ui.dialog;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.CheckBox;
import net.timafe.triptale.storage.LocalDir;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.BucketUrl;
import net.timafe.triptale.sync.BucketSyncer;
import net.timafe.triptale.sync.DesktopTrash;
import net.timafe.triptale.sync.S3Client;
import net.timafe.triptale.sync.SyncBase;
import net.timafe.triptale.sync.SyncBaseStore;
import net.timafe.triptale.sync.Trash;
import net.timafe.triptale.ui.UiText;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A sync dialog step that mirrors one data dir folder with the bucket via {@link BucketSyncer}'s
 * three-way merge (todo 33c): the Backpack row in Smart Sync ({@code attachments/}) and the
 * Tracks row in Sync Tracks ({@code radio/}). Owns the row's texts and its sync base
 * ({@code .triptale/sync/<dirName>.yml} in the data dir); the dialog decides
 * when to {@link #prepare} and {@link #run}.
 * <p>
 * A plan with deletes above {@link BucketSyncer.Plan#exceedsDeleteThreshold()} adds an explicit
 * "Yes, delete N files" checkbox to the row — {@link #readyToRun()} only turns true once it's
 * ticked, so a wiped or mis-mounted folder can't silently delete everything on the other side.
 */
final class BucketSyncStep {

    final SyncRow row;
    private final Path root;
    private final Path dataDir;
    private final String dirName;
    private final SyncBaseStore baseStore = new SyncBaseStore();
    private final Trash trash = new DesktopTrash();
    private BucketSyncer syncer;
    private String keyPrefix;
    private String bucketUrl;
    private SyncBase base;
    private BucketSyncer.Plan plan;
    private CheckBox confirmDeletes;

    BucketSyncStep(SyncRow row, Path root, String dirName) {
        this.row = row;
        this.root = root;
        this.dataDir = root.getParent();
        this.dirName = dirName;
    }

    /** Validates the S3 config and builds the syncer; a message if the folder can't be synced. */
    Optional<String> configure(AppSettings.Attachments settings) {
        Optional<String> invalid = settings.validationError();
        if (invalid.isPresent()) return invalid;
        try {
            syncer = new BucketSyncer(S3Client.from(settings));
            bucketUrl = settings.getBucketUrl();
            keyPrefix = BucketSyncer.keyPrefix(BucketUrl.parse(bucketUrl), dirName);
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
        base = baseStore.load(syncBaseFile(), bucketUrl, keyPrefix);
        Task<BucketSyncer.Plan> task = new Task<>() {
            @Override protected BucketSyncer.Plan call() {
                return syncer.plan(root, keyPrefix, base);
            }
        };
        task.setOnSucceeded(e -> {
            plan = task.getValue();
            if (plan.upToDate()) {
                row.idle("Up to date");
                row.setEnabled(true, false);
            } else {
                List<String> parts = new ArrayList<>();
                if (plan.toDownload() > 0) {
                    parts.add("↓ " + plan.toDownload() + " to download (" + megabytes(plan.downloadBytes()) + ")");
                }
                if (plan.toUpload() > 0) {
                    parts.add("↑ " + plan.toUpload() + " to upload (" + megabytes(plan.uploadBytes()) + ")");
                }
                if (plan.deleteLocal() > 0) parts.add("✕ " + plan.deleteLocal() + " local");
                if (plan.deleteRemote() > 0) parts.add("✕ " + plan.deleteRemote() + " remote");
                row.idle(String.join(" · ", parts));
                if (plan.exceedsDeleteThreshold()) addDeleteConfirmation();
                row.setEnabled(true, true);
            }
            onDone.run();
        });
        task.setOnFailed(e -> {
            row.fail("Cannot list bucket: " + UiText.describe(task.getException()));
            row.setEnabled(true, false);
            onDone.run();
        });
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void addDeleteConfirmation() {
        confirmDeletes = new CheckBox("Yes, delete " + plan.totalDeletes() + " file"
                + (plan.totalDeletes() == 1 ? "" : "s") + " — this is a lot, double-check the folder is right");
        confirmDeletes.getStyleClass().add("sync-status-error");
        confirmDeletes.selectedProperty().addListener((obs, was, is) -> row.notifyCheckChanged());
        row.extra.getChildren().add(confirmDeletes);
        row.layoutChanged();
    }

    /** False while a mass-delete plan is waiting for its extra confirmation checkbox. */
    boolean readyToRun() {
        return confirmDeletes == null || confirmDeletes.isSelected();
    }

    /** Runs the plan prepared by {@link #prepare}; call on a worker thread. */
    void run() {
        fx(() -> row.running("Syncing…"));
        try {
            BucketSyncer.Result result = syncer.sync(root, keyPrefix, base, plan, trash,
                    (done, total, file) -> fx(() -> row.running("Syncing " + (done + 1) + "/" + total + ": " + file)),
                    updated -> baseStore.save(syncBaseFile(), updated));
            List<String> parts = new ArrayList<>();
            if (result.downloaded() > 0) parts.add(result.downloaded() + " downloaded");
            if (result.uploaded() > 0) parts.add(result.uploaded() + " uploaded");
            if (result.deletedLocal() > 0) parts.add(result.deletedLocal() + " deleted locally");
            if (result.deletedRemote() > 0) parts.add(result.deletedRemote() + " deleted remotely");
            fx(() -> row.ok(parts.isEmpty() ? "Up to date" : String.join(", ", parts)));
        } catch (RuntimeException e) {
            fx(() -> row.fail("Sync failed: " + UiText.describe(e)));
        }
    }

    private Path syncBaseFile() {
        return LocalDir.syncBase(dataDir, dirName);
    }

    private static void fx(Runnable r) {
        Platform.runLater(r);
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0);
    }
}
