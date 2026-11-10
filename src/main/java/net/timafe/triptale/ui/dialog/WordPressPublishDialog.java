package net.timafe.triptale.ui.dialog;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.domain.Post;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.export.WordPressExporter;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.StatusSink;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.wordpress.PostPicker;
import net.timafe.triptale.wordpress.WordPressApiClient;
import net.timafe.triptale.wordpress.WordPressException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Sends a Post (todos 62, 85) to WordPress: the Post's title, intro and range are edited in the
 * Post Office, this dialog only shows what will be sent and sends it. Follows todo 60's send
 * rules (drafts only, conflict check before an update); the network calls run on worker threads,
 * the Post file is written only after a successful send. Stays open and shows the result.
 */
public final class WordPressPublishDialog {

    private enum Action { CREATE, UPDATE, CANCEL }

    private final WordPressExporter exporter;
    private final MarkdownStore store;
    private final AppSettings.WordPress wp;
    private final BrowserLauncher browser;
    private final StatusSink statusSink;
    private final BiConsumer<String, String> addPending;
    private final WordPressApiClient api = new WordPressApiClient();

    private Trip trip;
    private Post post;
    private boolean images;
    private boolean favesOnly;

    private final Label postLabel = new Label();
    private final Label resultLabel = new Label();
    private final Hyperlink openLink = new Hyperlink("Open in WordPress");
    private final CheckBox sendTagsBox = new CheckBox();
    private Button sendBtn;
    private Dialog<ButtonType> dlg;

    public WordPressPublishDialog(WordPressExporter exporter, MarkdownStore store, AppSettings.WordPress wp,
                                  BrowserLauncher browser, StatusSink statusSink,
                                  BiConsumer<String, String> addPending) {
        this.exporter = exporter;
        this.store = store;
        this.wp = wp;
        this.browser = browser;
        this.statusSink = statusSink;
        this.addPending = addPending;
    }

    /** {@code post} is the saved Post to send, {@code images}/{@code favesOnly} the Composer's image choice. */
    public void show(Window owner, Trip trip, Post post, boolean images, boolean favesOnly) {
        this.trip = trip;
        this.post = post;
        this.images = images;
        this.favesOnly = favesOnly;

        postLabel.setText(PostPicker.label(trip, post));
        postLabel.setWrapText(true);
        resultLabel.setWrapText(true);
        openLink.setVisible(false);
        Label hint = new Label(post.wpId() == null || post.wpId().isBlank()
                ? "A new draft will be created on WordPress."
                : "The existing WordPress post " + post.wpId() + " will be updated.");
        hint.getStyleClass().add("sync-status-muted");
        // Tags are always sent with a new draft; on an update they would replace the ones edited on WordPress, so opt in
        boolean update = post.wpId() != null && !post.wpId().isBlank();
        sendTagsBox.setText("Also send tags (" + String.join("; ", post.tags()) + "), replacing those on WordPress");
        sendTagsBox.setSelected(false);
        sendTagsBox.setVisible(update && !post.tags().isEmpty());
        sendTagsBox.setManaged(sendTagsBox.isVisible());
        HBox result = new HBox(8, resultLabel, openLink);
        result.setAlignment(Pos.CENTER_LEFT);

        dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("📡 Send to WordPress");
        dlg.setHeaderText(trip.name());
        dlg.setResizable(true);
        dlg.getDialogPane().setContent(new VBox(8, postLabel, hint, sendTagsBox, result));
        Dialogs.applyStylesheet(dlg.getDialogPane());

        ButtonType sendType = new ButtonType("📡 Send to WordPress", ButtonBar.ButtonData.OTHER);
        dlg.getDialogPane().getButtonTypes().setAll(sendType, ButtonType.CLOSE);
        sendBtn = (Button) dlg.getDialogPane().lookupButton(sendType);
        sendBtn.setDefaultButton(true);
        // Consumed so the dialog stays open and shows the result
        sendBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume();
            send();
        });
        dlg.showAndWait();
    }

    private void send() {
        Post draft = post;
        String title = draft.title();
        String markup;
        try {
            markup = exporter.exportPost(trip, draft, images, favesOnly);
            if (!exporter.lastInlineImageProblems().isEmpty()) {
                statusSink.status("⚠ Inline images: " + String.join("; ", exporter.lastInlineImageProblems()));
            }
        } catch (RuntimeException e) {
            result("❌ Export failed: " + UiText.describe(e), false);
            return;
        }
        busy(true);
        boolean sendTags = sendTagsBox.isSelected();
        boolean sent = draft.wpId() != null && !draft.wpId().isBlank();
        if (!sent) {
            run(() -> api.createDraft(wp, title, markup, draft.tags()), r -> finish(draft, r, true));
            return;
        }
        run(() -> fetch(draft.wpId()), existing -> {
            Action action = decide(existing, draft);
            switch (action) {
                case CANCEL -> {
                    busy(false);
                    result("Cancelled, nothing sent", null);
                }
                case CREATE -> run(() -> api.createDraft(wp, title, markup, draft.tags()), r -> finish(draft, r, true));
                case UPDATE -> run(() -> api.updatePost(wp, draft.wpId(), title, markup, sendTags ? draft.tags() : null), r -> finish(draft, r, false));
            }
        });
    }

    /** The existing post, or empty if it's gone on WordPress. */
    private Optional<WordPressApiClient.PostResult> fetch(String id) {
        try {
            return Optional.of(api.getPost(wp, id));
        } catch (WordPressApiClient.NotFoundException e) {
            return Optional.empty();
        }
    }

    /** Todo 60's send rules, asked on the FX thread on top of this dialog. */
    private Action decide(Optional<WordPressApiClient.PostResult> existing, Post draft) {
        if (existing.isEmpty()) {
            return confirm("WordPress post not found", "The post " + draft.wpId()
                    + " was not found on WordPress (deleted or trashed?). Create a new draft instead?")
                    ? Action.CREATE : Action.CANCEL;
        }
        WordPressApiClient.PostResult post = existing.get();
        if (draft.wpModified() != null && post.modified() != null && post.modified().isAfter(draft.wpModified())) {
            ButtonType overwrite = new ButtonType("Overwrite", ButtonBar.ButtonData.OTHER);
            ButtonType createNew = new ButtonType("Create New Draft", ButtonBar.ButtonData.OTHER);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "This post was edited on WordPress since the last send (e.g. images or wording). "
                            + "Overwrite it with TripTale's version, create a new draft instead, or cancel?",
                    overwrite, createNew, ButtonType.CANCEL);
            alert.initOwner(dlg.getDialogPane().getScene().getWindow());
            alert.setHeaderText("WordPress post changed since last send");
            Dialogs.applyStylesheet(alert.getDialogPane());
            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) return Action.CANCEL;
            if (choice.get() == createNew) return Action.CREATE;
        }
        if ("publish".equals(post.status()) && !confirm("Published post",
                "This post is published on WordPress — overwriting goes live immediately. Continue?")) {
            return Action.CANCEL;
        }
        return Action.UPDATE;
    }

    /** Saves the Post with the returned WordPress fields; WordPress already has it, so report a failed save loudly. */
    private void finish(Post draft, WordPressApiClient.PostResult r, boolean created) {
        busy(false);
        Post saved = draft.withWordPress(r.id(), r.modified());
        String what = (created ? "Created draft " : "Updated ") + r.id();
        try {
            store.savePost(trip.ref(), saved);
        } catch (RuntimeException e) {
            result("⚠ " + what + " on WordPress, but saving the Post file failed: " + UiText.describe(e), false);
            return;
        }
        addPending.accept(trip.ref().path() + "/posts/" + saved.slug(), "Update");
        post = saved;
        postLabel.setText(PostPicker.label(trip, saved));
        result("✅ " + what, true);
        String editor = PostPicker.editorUrl(wp.getSite(), r.id());
        openLink.setOnAction(e -> browser.open(editor));
        openLink.setVisible(true);
        statusSink.status("WordPress: " + what);
    }

    private <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            busy(false);
            Throwable ex = task.getException();
            result("❌ " + (ex instanceof WordPressException ? ex.getMessage() : UiText.describe(ex)), false);
        });
        Thread thread = new Thread(task, "wordpress-send");
        thread.setDaemon(true);
        thread.start();
    }

    private void busy(boolean busy) {
        sendBtn.setDisable(busy);
        if (busy) {
            openLink.setVisible(false);
            result("Sending…", null);
        }
    }

    /** {@code ok}: true green, false red, null neutral. */
    private void result(String text, Boolean ok) {
        resultLabel.setText(text);
        resultLabel.getStyleClass().removeAll("sync-status-ok", "sync-status-error", "sync-status-muted");
        resultLabel.getStyleClass().add(ok == null ? "sync-status-muted" : ok ? "sync-status-ok" : "sync-status-error");
    }

    private boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.initOwner(dlg.getDialogPane().getScene().getWindow());
        alert.setHeaderText(header);
        Dialogs.applyStylesheet(alert.getDialogPane());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }
}
