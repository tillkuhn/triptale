package net.timafe.triptale.ui.dialog;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;

import java.util.Optional;

/**
 * Sync Attachments (todo 33c): a standalone three-way merge of {@code attachments/} with the
 * bucket — download, upload, and propagate deletes — outside of Smart Sync's git flow, for when
 * only the backpack needs attention (e.g. after deleting a mistakenly imported file or a
 * re-rendered trip map). Replaces the old one-directional Push/Pull Backpack actions, which
 * couldn't express a delete. Mirrors {@link SyncTracksDialog}.
 */
public final class SyncAttachmentsDialog {

    private static final ButtonType SYNC = new ButtonType("🔄 Sync Now", ButtonBar.ButtonData.OK_DONE);

    private final SettingsStore settingsStore;
    private final AttachmentsDir attachmentsDir;

    // Per-show state; a SyncAttachmentsDialog instance is meant to be shown once.
    private BucketSyncStep step;
    private Button syncButton;
    private Button closeButton;
    private boolean preparing;
    private boolean running;
    private Dialog<Void> dialog;

    public SyncAttachmentsDialog(SettingsStore settingsStore, AttachmentsDir attachmentsDir) {
        this.settingsStore = settingsStore;
        this.attachmentsDir = attachmentsDir;
    }

    /** Shows the dialog and starts comparing with the bucket. Returns immediately. */
    public void show() {
        AppSettings.Attachments settings = settingsStore.load().getAttachments();

        GridPane grid = SyncRow.grid();
        step = new BucketSyncStep(new SyncRow("Backpack", false, this::updateSyncButton, this::resize),
                attachmentsDir.root(), AttachmentsDir.DIR_NAME);
        step.row.addTo(grid, 0);

        Label context = new Label(UiText.homeRelative(attachmentsDir.root()) + "  →  "
                + (settings.getBucketUrl() == null || settings.getBucketUrl().isBlank()
                        ? "(no bucket)" : settings.getBucketUrl()));
        context.setWrapText(true);
        context.setMinHeight(Region.USE_PREF_SIZE);
        context.setStyle("-fx-font-size: 11; -fx-opacity: 0.7;");
        grid.add(context, 0, 1, 4, 1);

        dialog = new Dialog<>();
        dialog.setTitle("Sync Attachments");
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE, SYNC);
        Dialogs.applyStylesheet(dialog.getDialogPane());
        syncButton = (Button) dialog.getDialogPane().lookupButton(SYNC);
        closeButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        syncButton.setDefaultButton(true);
        closeButton.setDefaultButton(false);
        // Sync runs in place; consuming the event keeps the dialog open.
        syncButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            runSync();
        });
        dialog.setOnCloseRequest(e -> {
            if (running) e.consume();
        });

        prepare(settings);
        updateSyncButton();
        dialog.show();
    }

    private void prepare(AppSettings.Attachments settings) {
        step.row.setEnabled(false, false);
        Optional<String> problem = settings.getSync() == AppSettings.AttachmentSync.CLOUD
                ? step.configure(settings)
                : Optional.of("Needs attachment sync mode 'cloud' (⚙ Edit Settings…)");
        if (problem.isPresent()) {
            step.row.muted(problem.get());
            return;
        }
        preparing = true;
        step.prepare("sync-attachments-plan", () -> {
            preparing = false;
            updateSyncButton();
        });
    }

    private void runSync() {
        running = true;
        syncButton.setDisable(true);
        closeButton.setDisable(true);
        Thread worker = new Thread(() -> {
            step.run();
            Platform.runLater(() -> {
                running = false;
                syncButton.setDefaultButton(false);
                closeButton.setDisable(false);
                closeButton.setDefaultButton(true);
                closeButton.requestFocus();
            });
        }, "sync-attachments-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Enabled once the plan found something to transfer and any mass-delete is confirmed. */
    private void updateSyncButton() {
        syncButton.setDisable(running || preparing || !step.row.check.isSelected() || !step.readyToRun());
    }

    /** Grows (or shrinks) the window to fit after the row changes its text. */
    private void resize() {
        Platform.runLater(() -> {
            if (dialog == null || dialog.getDialogPane().getScene() == null) return;
            Window window = dialog.getDialogPane().getScene().getWindow();
            if (window != null) window.sizeToScene();
        });
    }
}
