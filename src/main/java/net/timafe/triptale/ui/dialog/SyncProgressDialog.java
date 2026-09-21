package net.timafe.triptale.ui.dialog;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import net.timafe.triptale.git.GitService;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * All-in-one remote sync: commits any outstanding changes (in-memory pending saves as well as
 * any changes made to the data dir outside the app), then fetches and rebases onto the remote
 * branch, then pushes.
 * <p>
 * The git operations run on a daemon thread while a small modeless popup tracks the current
 * step. The popup closes itself on success; on failure it stays open showing which step failed
 * and why, and only then reveals its Close button.
 */
public final class SyncProgressDialog {

    private final GitService gitService;
    private final SettingsStore settingsStore;

    public SyncProgressDialog(GitService gitService, SettingsStore settingsStore) {
        this.gitService = gitService;
        this.settingsStore = settingsStore;
    }

    /**
     * Shows the popup and starts the worker. Returns immediately.
     *
     * @param commitMessage message for the initial commit of outstanding changes
     * @param onSuccess     invoked on the FX thread with the new commit SHA (null when there was
     *                      nothing to commit), before the popup closes
     */
    public void start(String commitMessage, Consumer<String> onSuccess) {
        String dataDir = settingsStore.load().resolvedDataDir()
                .map(Path::toString).orElse("(not configured)");
        String remote = remoteDisplay();

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(22, 22);
        spinner.setMinSize(22, 22);
        Label opLabel = new Label("Starting sync…");
        opLabel.setWrapText(true);
        opLabel.setMaxWidth(Double.MAX_VALUE);
        HBox stepRow = new HBox(10, spinner, opLabel);
        stepRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(opLabel, Priority.ALWAYS);

        Label contextLabel = new Label("Data dir: " + dataDir + "\nRemote: " + remote);
        contextLabel.setWrapText(true);
        contextLabel.setMaxWidth(Double.MAX_VALUE);
        contextLabel.setStyle("-fx-font-size: 11; -fx-opacity: 0.7;");

        VBox content = new VBox(10, stepRow, contextLabel);
        content.setPadding(new Insets(16));
        content.setMinWidth(480);
        content.setPrefWidth(520);

        Dialog<Void> progress = new Dialog<>();
        progress.setTitle("Sync");
        progress.getDialogPane().setContent(content);
        progress.getDialogPane().setMinWidth(520);
        progress.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Node closeButton = progress.getDialogPane().lookupButton(ButtonType.CLOSE);
        closeButton.setVisible(false);
        closeButton.setManaged(false);
        Dialogs.applyStylesheet(progress.getDialogPane());
        progress.show();

        Thread worker = new Thread(() -> {
            // Tracks which of the three steps is in flight, so a failure can name it.
            String step = "Commit";
            try {
                Platform.runLater(() -> opLabel.setText("Committing outstanding changes in " + dataDir + "…"));
                String sha = gitService.commitAll(commitMessage);

                step = "Rebase";
                Platform.runLater(() -> opLabel.setText("Fetching & rebasing from " + remote + "…"));
                gitService.fetchAndRebase();

                step = "Push";
                Platform.runLater(() -> opLabel.setText("Pushing to " + remote + "…"));
                gitService.push();

                String finalSha = sha;
                Platform.runLater(() -> {
                    onSuccess.accept(finalSha);
                    progress.close();
                });
            } catch (RuntimeException e) {
                String failedStep = step;
                Platform.runLater(() -> {
                    spinner.setVisible(false);
                    spinner.setManaged(false);
                    opLabel.setText(failedStep + " failed: " + UiText.describe(e));
                    closeButton.setVisible(true);
                    closeButton.setManaged(true);
                });
            }
        }, "sync-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private String remoteDisplay() {
        try {
            String remote = gitService.remoteUrl();
            return remote.isBlank() ? "(no remote)" : remote;
        } catch (RuntimeException e) {
            return "(no remote)";
        }
    }
}
