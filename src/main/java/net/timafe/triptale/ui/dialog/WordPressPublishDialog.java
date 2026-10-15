package net.timafe.triptale.ui.dialog;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Sends the Diary Composer's date range to WordPress (todo 62, see
 * {@code docs/62_wordpress_publish_dialog.md}): pick one of the trip's Posts or create a new one,
 * edit title and intro, send. The range wins over the Post's stored days. Follows todo 60's send
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
    private List<LocalDate> dates;
    private boolean images;
    private boolean favesOnly;
    private final List<Post> posts = new ArrayList<>();

    private final ComboBox<Post> postCombo = new ComboBox<>();
    private final TextField titleField = new TextField();
    private final TextArea introArea = new TextArea();
    private final Label daysLabel = new Label();
    private final Label resultLabel = new Label();
    private final Hyperlink openLink = new Hyperlink("Open in WordPress");
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

    /** {@code dates} are the entry days of the Composer's range, {@code images}/{@code favesOnly} its image choice. */
    public void show(Window owner, Trip trip, List<LocalDate> dates, boolean images, boolean favesOnly) {
        this.trip = trip;
        this.dates = List.copyOf(dates);
        this.images = images;
        this.favesOnly = favesOnly;
        try {
            posts.addAll(store.listPosts(trip.ref()));
        } catch (RuntimeException e) {
            statusSink.error("Could not load Posts: " + e.getMessage());
            return;
        }

        postCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Post p) { return p == null ? "-- create new post --" : PostPicker.label(trip, p); }
            @Override public Post fromString(String s) { return null; }
        });
        postCombo.setMaxWidth(Double.MAX_VALUE);
        postCombo.valueProperty().addListener((obs, old, p) -> fill(p));
        introArea.setWrapText(true);
        introArea.setPrefRowCount(4);
        introArea.setPromptText("Optional lead paragraph (Markdown)");
        titleField.setPrefColumnCount(40);
        resultLabel.setWrapText(true);
        openLink.setVisible(false);

        GridPane grid = Dialogs.formGrid();
        grid.addRow(0, new Label("Post:"), postCombo);
        grid.addRow(1, new Label("Days:"), daysLabel);
        grid.addRow(2, new Label("Title:"), titleField);
        grid.addRow(3, new Label("Intro:"), introArea);
        GridPane.setHgrow(postCombo, Priority.ALWAYS);
        HBox result = new HBox(8, resultLabel, openLink);
        result.setAlignment(Pos.CENTER_LEFT);

        dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("📡 Send to WordPress");
        dlg.setHeaderText(trip.name() + " · " + PostPicker.days(trip, this.dates)
                + " · " + this.dates.size() + (this.dates.size() == 1 ? " entry" : " entries"));
        dlg.setResizable(true);
        dlg.getDialogPane().setContent(new VBox(8, grid, result));
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

        refreshPosts(PostPicker.preselect(posts, this.dates).orElse(null));
        dlg.showAndWait();
    }

    private void refreshPosts(Post select) {
        List<Post> items = new ArrayList<>();
        items.add(null);
        items.addAll(posts);
        postCombo.getItems().setAll(items);
        postCombo.setValue(select);
        fill(select);
    }

    /** Prefills title/intro and the days line for {@code post} (null = create new). */
    private void fill(Post post) {
        titleField.setText(post == null ? PostPicker.defaultTitle(trip, dates) : post.title());
        introArea.setText(post == null || post.intro() == null ? "" : post.intro().strip());
        String days = PostPicker.days(trip, dates);
        daysLabel.setText(post == null ? days
                : PostPicker.daysChange(trip, post, dates).map(c -> "⚠ " + c).orElse(days));
    }

    private void send() {
        String title = titleField.getText() == null ? "" : titleField.getText().strip();
        if (title.isEmpty()) {
            result("❌ A title is required", false);
            return;
        }
        Post selected = postCombo.getValue();
        String slug = selected != null ? selected.slug()
                : PostPicker.uniqueSlug(title, posts.stream().map(Post::slug).toList());
        Post draft = new Post(slug, title, introArea.getText().strip(), dates,
                selected == null ? null : selected.wpId(), selected == null ? null : selected.wpModified());
        String markup;
        try {
            markup = exporter.exportPost(trip, draft, images, favesOnly);
        } catch (RuntimeException e) {
            result("❌ Export failed: " + UiText.describe(e), false);
            return;
        }
        busy(true);
        boolean sent = draft.wpId() != null && !draft.wpId().isBlank();
        if (!sent) {
            run(() -> api.createDraft(wp, title, markup), r -> finish(selected, draft, r, true));
            return;
        }
        run(() -> fetch(draft.wpId()), existing -> {
            Action action = decide(existing, draft);
            switch (action) {
                case CANCEL -> {
                    busy(false);
                    result("Cancelled, nothing sent", null);
                }
                case CREATE -> run(() -> api.createDraft(wp, title, markup), r -> finish(selected, draft, r, true));
                case UPDATE -> run(() -> api.updatePost(wp, draft.wpId(), title, markup), r -> finish(selected, draft, r, false));
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
    private void finish(Post selected, Post draft, WordPressApiClient.PostResult r, boolean created) {
        busy(false);
        Post saved = draft.withWordPress(r.id(), r.modified());
        String what = (created ? "Created draft " : "Updated ") + r.id();
        try {
            store.savePost(trip.ref(), saved);
        } catch (RuntimeException e) {
            result("⚠ " + what + " on WordPress, but saving the Post file failed: " + UiText.describe(e), false);
            return;
        }
        addPending.accept(trip.ref().path() + "/posts/" + saved.slug(), selected == null ? "Create" : "Update");
        if (selected != null) posts.remove(selected);
        posts.add(saved);
        posts.sort(Comparator.comparing(Post::title, Comparator.nullsLast(Comparator.naturalOrder())));
        refreshPosts(saved);
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
        postCombo.setDisable(busy);
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
