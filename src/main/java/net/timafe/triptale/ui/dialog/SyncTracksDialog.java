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
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.radio.RadioLibrary;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;

import java.util.Optional;

/**
 * Sync Tracks (todo 52): mirrors {@code radio/} with the attachments bucket under its own
 * {@code [<prefix>/]radio/} key prefix, the same way Smart Sync's Backpack row does for
 * {@code attachments/} — pull missing tracks, then push new or changed ones, never delete.
 * Kept out of Smart Sync on purpose, so music never slows down a diary sync.
 * <p>
 * Like Smart Sync, it only compares on open and transfers when the user presses Sync. A failing
 * bucket listing doubles as the connectivity check. Needs attachment sync mode {@code cloud}.
 */
public final class SyncTracksDialog {

    private static final ButtonType SYNC = new ButtonType("Sync", ButtonBar.ButtonData.OK_DONE);

    private final SettingsStore settingsStore;
    private final RadioLibrary radioLibrary;

    // Per-show state; a SyncTracksDialog instance is meant to be shown once.
    private BucketSyncStep tracks;
    private Button syncButton;
    private Button closeButton;
    private boolean preparing;
    private boolean running;
    private Dialog<Void> dialog;

    public SyncTracksDialog(SettingsStore settingsStore, RadioLibrary radioLibrary) {
        this.settingsStore = settingsStore;
        this.radioLibrary = radioLibrary;
    }

    /** Shows the dialog and starts comparing with the bucket. Returns immediately. */
    public void show() {
        AppSettings.Attachments settings = settingsStore.load().getAttachments();

        GridPane grid = SyncRow.grid();
        tracks = new BucketSyncStep(new SyncRow("Tracks", false, () -> {}, this::resize),
                radioLibrary.root(), RadioLibrary.DIR_NAME);
        tracks.row.addTo(grid, 0);

        Label context = new Label(UiText.homeRelative(radioLibrary.root()) + "  →  "
                + (settings.getBucketUrl() == null || settings.getBucketUrl().isBlank()
                        ? "(no bucket)" : settings.getBucketUrl()));
        context.setWrapText(true);
        context.setMinHeight(Region.USE_PREF_SIZE);
        context.setStyle("-fx-font-size: 11; -fx-opacity: 0.7;");
        grid.add(context, 0, 1, 4, 1);

        dialog = new Dialog<>();
        dialog.setTitle("Sync Tracks");
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
        tracks.row.setEnabled(false, false);
        Optional<String> problem = settings.getSync() == AppSettings.AttachmentSync.CLOUD
                ? tracks.configure(settings)
                : Optional.of("Needs attachment sync mode 'cloud' (⚙ Edit Settings…)");
        if (problem.isPresent()) {
            tracks.row.muted(problem.get());
            return;
        }
        preparing = true;
        tracks.prepare("sync-tracks-plan", () -> {
            preparing = false;
            updateSyncButton();
        });
    }

    private void runSync() {
        running = true;
        syncButton.setDisable(true);
        closeButton.setDisable(true);
        Thread worker = new Thread(() -> {
            tracks.run();
            Platform.runLater(() -> {
                running = false;
                syncButton.setDefaultButton(false);
                closeButton.setDisable(false);
                closeButton.setDefaultButton(true);
                closeButton.requestFocus();
            });
        }, "sync-tracks-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Enabled once the plan found something to transfer; a finished run doesn't re-enable it. */
    private void updateSyncButton() {
        syncButton.setDisable(running || preparing || !tracks.row.check.isSelected());
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
