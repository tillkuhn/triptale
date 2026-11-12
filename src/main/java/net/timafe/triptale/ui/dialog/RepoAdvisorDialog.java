package net.timafe.triptale.ui.dialog;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.BucketUrl;
import net.timafe.triptale.git.GitService;
import net.timafe.triptale.storage.ExifReader;
import net.timafe.triptale.storage.LocalDir;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.sync.BucketSyncer;
import net.timafe.triptale.sync.ObjectStore;
import net.timafe.triptale.sync.S3Client;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.util.ByteSizes;
import net.timafe.triptale.util.DirSizes;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Repo Advisor: read-only summary of the git (and, with cloud sync, S3 attachment) configuration
 * actually in effect, plus git object count and the approximate size of the synced folders and the
 * local {@code .triptale/} cache (todo 26). The 🧽 Janitor button runs the housekeeping tasks
 * (todo 81) and shows their output below.
 */
public final class RepoAdvisorDialog {

    private static final List<String> FOLDERS = List.of("attachments", "radio", LocalDir.NAME);
    private static final DateTimeFormatter RUN_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final GitService gitService;
    private final SettingsStore settingsStore;
    private final ExifReader exifReader;
    private final MarkdownStore store;

    public RepoAdvisorDialog(GitService gitService, SettingsStore settingsStore, ExifReader exifReader,
                             MarkdownStore store) {
        this.gitService = gitService;
        this.settingsStore = settingsStore;
        this.exifReader = exifReader;
        this.store = store;
    }

    private String lastRunDisplay() {
        try {
            return store.loadLastJanitorRun()
                    .map(t -> RUN_FORMAT.format(t.atZone(ZoneId.systemDefault())))
                    .orElse("never");
        } catch (RuntimeException e) {
            return "(error: " + UiText.describe(e) + ")";
        }
    }

    public void show() {
        AppSettings settings = settingsStore.load();
        GridPane grid = Dialogs.infoGrid();
        int row = 0;
        grid.add(new Label("Data dir:"), 0, row);
        grid.add(new Label(settings.resolvedDataDir().map(Path::toString).orElse("(not configured)")), 1, row++);
        grid.add(new Label("Origin URL:"), 0, row);
        grid.add(new Label(remoteDisplay()), 1, row++);
        grid.add(new Label("Author:"), 0, row);
        grid.add(new Label(authorDisplay(settings)), 1, row++);

        AppSettings.Attachments attachments = settings.getAttachments();
        boolean cloud = attachments.getSync() == AppSettings.AttachmentSync.CLOUD;
        if (cloud) {
            // credentials are deliberately never shown
            grid.add(new Label("Backpack:"), 0, row);
            grid.add(new Label(orNone(attachments.getBucketUrl())), 1, row++);
            grid.add(new Label("Region:"), 0, row);
            grid.add(new Label(orNone(attachments.getRegion())), 1, row++);
        }

        Optional<Path> dataDir = settings.resolvedDataDir();
        boolean ready = dataDir.isPresent() && gitService.isConfigured();
        grid.add(new Label("Git objects:"), 0, row);
        Label objectsLabel = new Label(ready ? objectsDisplay() : "-");
        grid.add(objectsLabel, 1, row++);
        Label triptaleSizeRef = null;
        for (String folder : FOLDERS) {
            grid.add(new Label(folder + "/:"), 0, row);
            Label size = new Label(ready ? sizeDisplay(dataDir.get(), folder, attachments, cloud) : "-");
            if (folder.equals(LocalDir.NAME)) triptaleSizeRef = size;
            grid.add(size, 1, row++);
        }
        Label triptaleLabel = triptaleSizeRef;
        Label lastRunLabel = new Label(ready ? lastRunDisplay() : "-");
        grid.add(new Label("Last janitor run:"), 0, row);
        grid.add(lastRunLabel, 1, row++);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("🧹 Repo Advisor");
        alert.setHeaderText(cloud ? "Git and backpack cloud configuration" : "Git configuration");
        Circle disc = new Circle(24, Color.web("#3f7fd0"));
        disc.setStroke(Color.WHITE);
        disc.setStrokeWidth(3);
        Label broom = new Label("🧹");
        broom.setStyle("-fx-font-size: 22px;");
        alert.setGraphic(new StackPane(disc, broom));

        TextArea log = new TextArea();
        log.setEditable(false);
        log.setPrefRowCount(12);
        log.setPrefColumnCount(70);
        log.setStyle("-fx-font-family: monospace;");
        log.setVisible(false);
        log.setManaged(false);
        VBox content = new VBox(8, grid, log);
        alert.getDialogPane().setContent(content);

        ButtonType janitor = new ButtonType("🧽 Janitor", ButtonBar.ButtonData.LEFT);
        ButtonType purge = new ButtonType("🗑 Purge Tiles Cache", ButtonBar.ButtonData.LEFT);
        alert.getButtonTypes().setAll(janitor, purge, ButtonType.CLOSE);
        Dialogs.applyStylesheet(alert.getDialogPane());
        Button janitorButton = (Button) alert.getDialogPane().lookupButton(janitor);
        Button purgeButton = (Button) alert.getDialogPane().lookupButton(purge);
        janitorButton.setDisable(!ready);
        purgeButton.setDisable(!ready);
        janitorButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume(); // keep the dialog open to show the output
            showLog(alert, log);
            runJanitor(janitorButton, log, objectsLabel, lastRunLabel);
        });
        purgeButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            showLog(alert, log);
            purgeTiles(purgeButton, log, dataDir.orElseThrow(), triptaleLabel);
        });
        alert.showAndWait();
    }

    private static void showLog(Alert alert, TextArea log) {
        log.setVisible(true);
        log.setManaged(true);
        alert.getDialogPane().getScene().getWindow().sizeToScene();
    }

    /** Not part of the Janitor: only worth doing when the disk space is needed, tiles refill on demand. */
    private void purgeTiles(Button button, TextArea log, Path dataDir, Label triptaleLabel) {
        button.setDisable(true);
        log.clear();
        log.appendText("▶ Purge tiles cache\n");
        Thread worker = new Thread(() -> {
            Path tiles = LocalDir.cache(dataDir, "tiles");
            long bytes = DirSizes.totalBytes(tiles);
            try {
                int files = LocalDir.purgeCache(dataDir, "tiles");
                append(log, files + " tiles deleted, " + ByteSizes.humanize(bytes) + " freed\n✔ done");
            } catch (RuntimeException e) {
                append(log, "❌ FAILED: " + UiText.describe(e));
            }
            Platform.runLater(() -> {
                triptaleLabel.setText(ByteSizes.humanize(DirSizes.totalBytes(LocalDir.root(dataDir))) + " local");
                button.setDisable(false);
            });
        }, "repo-purge-tiles");
        worker.setDaemon(true);
        worker.start();
    }

    private record Task(String name, Supplier<String> action) { }

    private List<Task> tasks() {
        return List.of(
                new Task("git gc", gitService::gc),
                new Task("Sweep orphaned EXIF cache entries",
                        () -> exifReader.sweepOrphans() + " orphaned entries removed"));
    }

    /** Runs all tasks one after the other off the FX thread, appending their output to {@code log}. */
    private void runJanitor(Button button, TextArea log, Label objectsLabel, Label lastRunLabel) {
        button.setDisable(true);
        log.clear();
        Thread worker = new Thread(() -> {
            int failed = 0;
            for (Task task : tasks()) {
                append(log, "▶ " + task.name() + "\n");
                try {
                    String out = task.action().get();
                    append(log, (out == null || out.isBlank() ? "(no output)" : out) + "\n✔ done\n\n");
                } catch (RuntimeException e) {
                    failed++;
                    append(log, "❌ FAILED: " + UiText.describe(e) + "\n\n");
                }
            }
            append(log, failed == 0 ? "✅ All tasks finished."
                    : "❌ " + failed + (failed == 1 ? " task" : " tasks") + " FAILED, see above.");
            Platform.runLater(() -> {
                try {
                    store.saveLastJanitorRun(Instant.now());
                } catch (RuntimeException e) {
                    log.appendText("\n❌ Could not store the run time: " + UiText.describe(e));
                }
                lastRunLabel.setText(lastRunDisplay());
                objectsLabel.setText(objectsDisplay());
                button.setDisable(false);
            });
        }, "repo-janitor");
        worker.setDaemon(true);
        worker.start();
    }

    private static void append(TextArea log, String text) {
        Platform.runLater(() -> log.appendText(text));
    }

    private String objectsDisplay() {
        try {
            return gitService.objectSummary();
        } catch (RuntimeException e) {
            return "(error: " + UiText.describe(e) + ")";
        }
    }

    /** Local size, plus the bucket total (one LIST) for the synced folders when cloud sync is on. */
    private static String sizeDisplay(Path dataDir, String folder, AppSettings.Attachments attachments, boolean cloud) {
        String local = ByteSizes.humanize(DirSizes.totalBytes(dataDir.resolve(folder))) + " local";
        if (!cloud || folder.equals(LocalDir.NAME)) return local;
        try {
            String prefix = BucketSyncer.keyPrefix(BucketUrl.parse(attachments.getBucketUrl()), folder);
            long bytes = S3Client.from(attachments).list(prefix).values().stream()
                    .mapToLong(ObjectStore.ObjectInfo::size).sum();
            return local + ", " + ByteSizes.humanize(bytes) + " in bucket";
        } catch (RuntimeException e) {
            return local + ", bucket: " + UiText.describe(e);
        }
    }

    private String remoteDisplay() {
        try {
            String remote = gitService.remoteUrl();
            return remote.isBlank() ? "(none)" : remote;
        } catch (RuntimeException e) {
            return "(error: " + e.getMessage() + ")";
        }
    }

    private static String orNone(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    /** Blank author settings mean JGit falls back to whatever the system git config says. */
    private static String authorDisplay(AppSettings settings) {
        String name = settings.getGit().getAuthorName();
        String email = settings.getGit().getAuthorEmail();
        return (name.isBlank() && email.isBlank())
                ? "(system git config)"
                : name + " <" + email + ">";
    }
}
