package net.timafe.triptale.ui.dialog;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import net.timafe.triptale.attachments.AttachmentPusher;
import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.attachments.S3Client;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.BucketUrl;
import net.timafe.triptale.git.GitService;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.ConnectivityService;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.util.CommitMessage;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Smart Sync (todo 32, see {@code docs/32_smart_sync.md}): one row per step — Connectivity,
 * Commit, Git Remote (fetch + rebase + push) and, in {@code cloud} mode, Attachments (push).
 * <p>
 * On open the dialog only <em>looks</em>: git status, a connectivity check, a {@code git fetch}
 * for ahead/behind counts and an attachments upload plan, each on a daemon thread. Nothing is
 * changed until the user presses Sync; the checked steps then run in order on a worker thread,
 * each row showing progress and result. The dialog stays open afterwards so results can be read.
 * <p>
 * The dialog owns its threads and reports back through a callback on the FX thread instead of
 * touching controller state.
 */
public final class SmartSyncDialog {

    /** What the app knows about its own unsaved-to-git work, for the default commit message. */
    public record Request(String pendingMessage, Collection<String> pendingLabels) {}

    public enum RemoteOutcome { NOT_RUN, OK, CONFLICT, FAILED }

    /**
     * Result of a sync run. {@code commitSettled} is true when the commit step ran and succeeded,
     * or the tree was clean anyway — either way the app's pending saves are now in git.
     * {@code sha} is the new commit, or null if none was made.
     */
    public record Outcome(boolean commitSettled, String sha, RemoteOutcome remote) {}

    private static final ButtonType SYNC = new ButtonType("Sync", ButtonBar.ButtonData.OK_DONE);

    private final GitService gitService;
    private final SettingsStore settingsStore;
    private final AttachmentsDir attachmentsDir;
    private final ConnectivityService connectivityService;
    private final BrowserLauncher browser;

    // Per-show state; a SmartSyncDialog instance is meant to be shown once.
    private Row connectivityRow;
    private Row commitRow;
    private Row remoteRow;
    private Row attachmentsRow;
    private TextArea messageArea;
    private TextArea remoteDetails;
    private TitledPane remoteDetailsPane;
    private Button revealButton;
    private Button syncButton;
    private Button closeButton;
    private List<String> dirtyFiles = List.of();
    private boolean commitClean;
    private AttachmentPusher pusher;
    private String keyPrefix;
    private Path attachmentsRoot;
    private int preparing;
    private boolean running;
    private Dialog<Void> dialog;

    public SmartSyncDialog(GitService gitService, SettingsStore settingsStore, AttachmentsDir attachmentsDir,
                           ConnectivityService connectivityService, BrowserLauncher browser) {
        this.gitService = gitService;
        this.settingsStore = settingsStore;
        this.attachmentsDir = attachmentsDir;
        this.connectivityService = connectivityService;
        this.browser = browser;
    }

    /**
     * Shows the dialog and starts the read-only checks. Returns immediately.
     *
     * @param onFinished invoked on the FX thread once a sync run has finished (not on Close
     *                   without syncing)
     */
    public void show(Request request, Consumer<Outcome> onFinished) {
        AppSettings settings = settingsStore.load();
        String remoteUrl = remoteUrl();

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        ColumnConstraints checkCol = new ColumnConstraints();
        ColumnConstraints nameCol = new ColumnConstraints(110);
        ColumnConstraints statusCol = new ColumnConstraints();
        statusCol.setHgrow(Priority.ALWAYS);
        statusCol.setFillWidth(true);
        ColumnConstraints iconCol = new ColumnConstraints(26);
        grid.getColumnConstraints().addAll(checkCol, nameCol, statusCol, iconCol);

        int r = 0;
        connectivityRow = new Row("Connectivity", false);
        connectivityRow.addTo(grid, r++);

        commitRow = new Row("Commit", true);
        messageArea = new TextArea();
        messageArea.setPrefRowCount(2);
        messageArea.setWrapText(true);
        commitRow.addTo(grid, r++);

        remoteRow = new Row("Git Remote", true);
        remoteDetails = new TextArea();
        remoteDetails.setEditable(false);
        remoteDetails.setPrefRowCount(6);
        remoteDetails.setWrapText(true);
        remoteDetailsPane = new TitledPane("Details", remoteDetails);
        remoteDetailsPane.setExpanded(false);
        remoteDetailsPane.expandedProperty().addListener((obs, was, is) -> resize());
        showNode(remoteDetailsPane, false);
        revealButton = new Button("Reveal in Finder");
        revealButton.setOnAction(e -> settings.resolvedDataDir()
                .ifPresent(dir -> browser.open(dir.toUri().toString())));
        showNode(revealButton, false);
        remoteRow.extra.getChildren().addAll(remoteDetailsPane, revealButton);
        remoteRow.addTo(grid, r++);

        boolean cloud = settings.getAttachments().getSync() == AppSettings.AttachmentSync.CLOUD;
        if (cloud) {
            attachmentsRow = new Row("Attachments", true);
            attachmentsRow.addTo(grid, r++);
        }

        Label context = new Label("Data dir: " + settings.resolvedDataDir().map(UiText::homeRelative)
                .orElse("(not configured)") + "\nRemote: " + (remoteUrl.isBlank() ? "(none)" : remoteUrl));
        context.setWrapText(true);
        context.setMinHeight(Region.USE_PREF_SIZE);
        context.setStyle("-fx-font-size: 11; -fx-opacity: 0.7;");
        grid.add(context, 0, r, 4, 1);

        dialog = new Dialog<>();
        dialog.setTitle("Smart Sync");
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(640);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE, SYNC);
        Dialogs.applyStylesheet(dialog.getDialogPane());
        syncButton = (Button) dialog.getDialogPane().lookupButton(SYNC);
        closeButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        syncButton.setDefaultButton(true);
        closeButton.setDefaultButton(false);
        // Sync runs in place; consuming the event keeps the dialog open.
        syncButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            runSync(request, cloud, onFinished);
        });
        dialog.setOnCloseRequest(e -> {
            if (running) e.consume();
        });

        prepareCommit(request);
        prepareRemote(settings, remoteUrl, cloud);
        updateSyncButton();
        dialog.show();
    }

    // ---------------------------------------------------------------------
    // Preparation (read-only)
    // ---------------------------------------------------------------------

    private void prepareCommit(Request request) {
        try {
            dirtyFiles = gitService.dirtyFiles();
        } catch (RuntimeException e) {
            commitRow.fail("Cannot read git status: " + UiText.describe(e));
            commitRow.setEnabled(false, false);
            return;
        }
        commitClean = dirtyFiles.isEmpty();
        if (commitClean) {
            commitRow.muted("Nothing to commit");
            commitRow.setEnabled(false, false);
            return;
        }
        List<String> external = CommitMessage.external(request.pendingLabels(), dirtyFiles);
        messageArea.setText(CommitMessage.compose(request.pendingMessage(), external));
        Label files = new Label(String.join("\n", dirtyFiles));
        files.setStyle("-fx-font-size: 11;");
        files.setMinHeight(Region.USE_PREF_SIZE);
        TitledPane filesPane = new TitledPane(plural(dirtyFiles.size(), "changed file"), files);
        filesPane.setExpanded(false);
        filesPane.expandedProperty().addListener((obs, was, is) -> resize());
        commitRow.extra.getChildren().addAll(messageArea, filesPane);
        commitRow.idle(plural(dirtyFiles.size(), "changed file")
                + (external.isEmpty() ? "" : " (" + external.size() + " changed outside the app)"));
        commitRow.setEnabled(true, true);
    }

    private void prepareRemote(AppSettings settings, String remoteUrl, boolean cloud) {
        boolean hasRemote = !remoteUrl.isBlank();
        String host = ConnectivityService.resolveHost(remoteUrl);
        remoteRow.setEnabled(false, false);
        if (!hasRemote) remoteRow.muted("No 'origin' remote configured");
        else remoteRow.muted("Waiting for connectivity check…");
        Optional<String> attachmentsProblem = cloud ? attachmentsProblem(settings) : Optional.empty();
        if (cloud) {
            attachmentsRow.setEnabled(false, false);
            attachmentsProblem.ifPresentOrElse(attachmentsRow::muted,
                    () -> attachmentsRow.muted("Waiting for connectivity check…"));
        }

        connectivityRow.running("Checking " + host + "…");
        preparing++;
        Task<Boolean> check = connectivityService.checkTask(remoteUrl);
        check.setOnSucceeded(e -> onConnectivity(Boolean.TRUE.equals(check.getValue()), host, hasRemote,
                cloud && attachmentsProblem.isEmpty()));
        check.setOnFailed(e -> onConnectivity(false, host, hasRemote, cloud && attachmentsProblem.isEmpty()));
        background("smart-sync-connectivity", check);
    }

    private void onConnectivity(boolean online, String host, boolean hasRemote, boolean attachmentsReady) {
        preparing--;
        if (!online) {
            connectivityRow.fail("Offline (" + host + " not reachable)");
            if (hasRemote) remoteRow.muted("Offline");
            if (attachmentsReady) attachmentsRow.muted("Offline");
            updateSyncButton();
            return;
        }
        connectivityRow.ok("Online (" + host + ")");
        if (hasRemote) prepareFetch();
        if (attachmentsReady) preparePlan();
        updateSyncButton();
    }

    private void prepareFetch() {
        remoteRow.running("Fetching from origin…");
        preparing++;
        Task<Optional<GitService.AheadBehind>> fetch = new Task<>() {
            @Override protected Optional<GitService.AheadBehind> call() {
                gitService.fetch();
                return gitService.aheadBehind();
            }
        };
        fetch.setOnSucceeded(e -> {
            preparing--;
            Optional<GitService.AheadBehind> ab = fetch.getValue();
            boolean willCommit = commitRow.check.isSelected();
            String newCommit = willCommit ? " (+ new commit)" : "";
            if (ab.isEmpty()) {
                remoteRow.idle("Remote branch not found — will push" + newCommit);
                remoteRow.setEnabled(true, true);
            } else if (ab.get().inSync() && !willCommit) {
                remoteRow.idle("Up to date");
                remoteRow.setEnabled(true, false);
            } else {
                remoteRow.idle("↓ " + ab.get().behind() + " incoming · ↑ " + ab.get().ahead() + " outgoing" + newCommit);
                remoteRow.setEnabled(true, true);
            }
            updateSyncButton();
        });
        fetch.setOnFailed(e -> {
            preparing--;
            remoteRow.fail("Fetch failed: " + UiText.describe(fetch.getException()));
            remoteRow.setEnabled(true, false);
            showDetails(UiText.describe(fetch.getException()), false);
            updateSyncButton();
        });
        background("smart-sync-fetch", fetch);
    }

    private void preparePlan() {
        attachmentsRow.running("Comparing with bucket…");
        preparing++;
        Task<AttachmentPusher.Plan> plan = new Task<>() {
            @Override protected AttachmentPusher.Plan call() {
                return pusher.plan(attachmentsRoot, keyPrefix);
            }
        };
        plan.setOnSucceeded(e -> {
            preparing--;
            AttachmentPusher.Plan p = plan.getValue();
            String remoteOnly = p.remoteOnly() == 0 ? "" : " · " + p.remoteOnly() + " only in cloud";
            if (p.upToDate()) {
                attachmentsRow.idle("Up to date" + remoteOnly);
                attachmentsRow.setEnabled(true, false);
            } else {
                attachmentsRow.idle("↑ " + p.toUpload() + " to upload (" + megabytes(p.uploadBytes()) + ")" + remoteOnly);
                attachmentsRow.setEnabled(true, true);
            }
            updateSyncButton();
        });
        plan.setOnFailed(e -> {
            preparing--;
            attachmentsRow.fail("Cannot list bucket: " + UiText.describe(plan.getException()));
            attachmentsRow.setEnabled(true, false);
            updateSyncButton();
        });
        background("smart-sync-plan", plan);
    }

    /** Validates the S3 config and builds the pusher; a message if attachments can't be synced. */
    private Optional<String> attachmentsProblem(AppSettings settings) {
        AppSettings.Attachments a = settings.getAttachments();
        Optional<String> invalid = a.validationError();
        if (invalid.isPresent()) return invalid;
        try {
            attachmentsRoot = attachmentsDir.root();
            pusher = new AttachmentPusher(S3Client.from(a));
            keyPrefix = AttachmentPusher.keyPrefix(BucketUrl.parse(a.getBucketUrl()));
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.of(UiText.describe(e));
        }
    }

    // ---------------------------------------------------------------------
    // Sync run
    // ---------------------------------------------------------------------

    private void runSync(Request request, boolean cloud, Consumer<Outcome> onFinished) {
        boolean doCommit = commitRow.check.isSelected();
        boolean doRemote = remoteRow.check.isSelected();
        boolean doAttachments = cloud && attachmentsRow.check.isSelected();
        String message = messageArea.getText().isBlank()
                ? CommitMessage.compose(request.pendingMessage(), List.of())
                : messageArea.getText().trim();
        running = true;
        for (Row row : rows()) row.check.setDisable(true);
        messageArea.setEditable(false);
        syncButton.setDisable(true);
        closeButton.setDisable(true);

        Thread worker = new Thread(() -> {
            boolean commitSettled = commitClean;
            boolean commitFailed = false;
            String sha = null;
            if (doCommit) {
                fx(() -> commitRow.running("Committing…"));
                try {
                    sha = gitService.commitAll(message);
                    String done = sha == null ? "Nothing to commit" : "Committed " + sha;
                    fx(() -> commitRow.ok(done));
                    commitSettled = true;
                } catch (RuntimeException e) {
                    commitFailed = true;
                    fx(() -> commitRow.fail("Commit failed: " + UiText.describe(e)));
                }
            }

            RemoteOutcome remote = RemoteOutcome.NOT_RUN;
            if (doRemote) {
                if (commitFailed || (!doCommit && !commitClean)) {
                    String why = commitFailed ? "commit failed" : "uncommitted changes (rebase needs a clean tree)";
                    fx(() -> remoteRow.muted("Skipped — " + why));
                } else {
                    remote = runRemote();
                }
            }

            if (doAttachments) runAttachments();

            Outcome outcome = new Outcome(commitSettled, sha, remote);
            fx(() -> {
                running = false;
                syncButton.setDefaultButton(false);
                closeButton.setDisable(false);
                closeButton.setDefaultButton(true);
                closeButton.requestFocus();
                onFinished.accept(outcome);
            });
        }, "smart-sync-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private RemoteOutcome runRemote() {
        fx(() -> remoteRow.running("Fetching & rebasing onto origin…"));
        try {
            gitService.fetchAndRebase();
        } catch (RuntimeException e) {
            String output = UiText.describe(e);
            List<String> conflicts = GitService.conflictPaths(output);
            if (!conflicts.isEmpty()) {
                fx(() -> {
                    remoteRow.fail("Conflict: remote changed the same files — push skipped");
                    showDetails("Conflicting files:\n  " + String.join("\n  ", conflicts)
                            + "\n\nResolve them manually (terminal or Tolaria), then sync again.\n\n" + output, true);
                });
                return RemoteOutcome.CONFLICT;
            }
            fx(() -> {
                remoteRow.fail("Rebase failed — push skipped");
                showDetails(output, false);
            });
            return RemoteOutcome.FAILED;
        }
        fx(() -> remoteRow.running("Pushing to origin…"));
        try {
            gitService.push();
        } catch (RuntimeException e) {
            fx(() -> {
                remoteRow.fail("Push failed");
                showDetails(UiText.describe(e), false);
            });
            return RemoteOutcome.FAILED;
        }
        fx(() -> remoteRow.ok("In sync with origin"));
        return RemoteOutcome.OK;
    }

    private void runAttachments() {
        fx(() -> attachmentsRow.running("Uploading…"));
        try {
            AttachmentPusher.Result result = pusher.push(attachmentsRoot, keyPrefix, (done, total, file) ->
                    fx(() -> attachmentsRow.running("Uploading " + (done + 1) + "/" + total + ": " + file)));
            fx(() -> attachmentsRow.ok(result.uploaded() + " uploaded, " + result.unchanged() + " unchanged"));
        } catch (RuntimeException e) {
            fx(() -> attachmentsRow.fail("Upload failed: " + UiText.describe(e)));
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void updateSyncButton() {
        boolean anyChecked = rows().stream().anyMatch(row -> row.check.isSelected());
        syncButton.setDisable(running || preparing > 0 || !anyChecked);
    }

    private List<Row> rows() {
        return attachmentsRow == null ? List.of(commitRow, remoteRow) : List.of(commitRow, remoteRow, attachmentsRow);
    }

    private void showDetails(String text, boolean conflict) {
        remoteDetails.setText(text);
        showNode(remoteDetailsPane, true);
        showNode(revealButton, conflict);
        resize();
    }

    /** Grows (or shrinks) the window to fit after rows expand, collapse or change their text. */
    private void resize() {
        Platform.runLater(() -> {
            if (dialog == null || dialog.getDialogPane().getScene() == null) return;
            Window window = dialog.getDialogPane().getScene().getWindow();
            if (window != null) window.sizeToScene();
        });
    }

    private String remoteUrl() {
        try {
            return gitService.remoteUrl();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void showNode(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static void fx(Runnable r) {
        Platform.runLater(r);
    }

    private static void background(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static String plural(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0);
    }

    /** One step: checkbox, name, status text (+ extra controls below it), status icon. */
    private final class Row {
        final CheckBox check = new CheckBox();
        final Label name;
        final Label status = new Label();
        final VBox extra = new VBox(6);
        final ProgressIndicator spinner = new ProgressIndicator();
        final Label icon = new Label();
        private final boolean selectable;

        Row(String title, boolean selectable) {
            this.selectable = selectable;
            name = new Label(title);
            name.setStyle("-fx-font-weight: bold;");
            status.setWrapText(true);
            status.setMaxWidth(Double.MAX_VALUE);
            status.setMinHeight(Region.USE_PREF_SIZE);
            spinner.setPrefSize(18, 18);
            spinner.setMaxSize(18, 18);
            check.selectedProperty().addListener((obs, was, is) -> {
                if (syncButton != null) updateSyncButton();
            });
        }

        void addTo(GridPane grid, int rowIndex) {
            if (selectable) grid.add(check, 0, rowIndex);
            grid.add(name, 1, rowIndex);
            VBox middle = new VBox(6, status, extra);
            grid.add(middle, 2, rowIndex);
            StackPane iconCell = new StackPane(spinner, icon);
            iconCell.setAlignment(Pos.TOP_CENTER);
            grid.add(iconCell, 3, rowIndex);
            for (Node n : List.of(check, name, middle, iconCell)) GridPane.setValignment(n, VPos.TOP);
            showNode(spinner, false);
        }

        void setEnabled(boolean enabled, boolean selected) {
            check.setDisable(!enabled);
            check.setSelected(enabled && selected);
        }

        void idle(String text) { show(text, "", "sync-status-idle"); }
        void muted(String text) { show(text, "–", "sync-status-muted"); }
        void ok(String text) { show(text, "✓", "sync-status-ok"); }
        void fail(String text) { show(text, "✗", "sync-status-error"); }

        void running(String text) {
            status.setText(text);
            status.getStyleClass().setAll("label", "sync-status-idle");
            showNode(icon, false);
            showNode(spinner, true);
            resize();
        }

        private void show(String text, String symbol, String styleClass) {
            status.setText(text);
            status.getStyleClass().setAll("label", styleClass);
            icon.setText(symbol);
            icon.getStyleClass().setAll("label", styleClass);
            showNode(spinner, false);
            showNode(icon, true);
            resize();
        }
    }
}
