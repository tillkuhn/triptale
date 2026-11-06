package net.timafe.triptale.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.beans.property.SimpleStringProperty;
import javafx.util.StringConverter;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Post;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.StatusSink;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.wordpress.PostPicker;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The Post Office (todo 85, see {@code docs/85_post_office_posts_as_first_class_chapters.md}): a
 * trip's Posts (chapters, each a date range with its own title and intro) in one table, with an
 * editor for the selected or new one underneath. Local content only, sending to WordPress
 * happens from the Diary Composer. Saving and deleting write the Post file right away and
 * register it as pending for the next sync.
 */
public final class PostOfficeDialog {

    private final MarkdownStore store;
    private final StatusSink statusSink;
    private final BiConsumer<String, String> addPending;
    private final SettingsStore settingsStore;
    private final BrowserLauncher browser;

    private Trip trip;
    private List<LocalDate> entryDates = List.of();
    private final List<Post> posts = new ArrayList<>();
    /** The Post being edited; null while the editor holds a new, unsaved one. */
    private Post editing;
    private boolean creating;

    private final TableView<Post> table = new TableView<>();
    private final TextField titleField = new TextField();
    private final ComboBox<LocalDate> fromPicker = new ComboBox<>();
    private final ComboBox<LocalDate> toPicker = new ComboBox<>();
    private Map<LocalDate, String> dayLabels = new HashMap<>();
    private final TextField tagsField = new TextField();
    private final TextArea introArea = new TextArea();
    private final Label errorLabel = new Label();
    private Button saveBtn;
    private Button deleteBtn;
    private Button openWpBtn;
    private Dialog<ButtonType> dlg;

    public PostOfficeDialog(MarkdownStore store, SettingsStore settingsStore, BrowserLauncher browser,
                            StatusSink statusSink, BiConsumer<String, String> addPending) {
        this.store = store;
        this.settingsStore = settingsStore;
        this.browser = browser;
        this.statusSink = statusSink;
        this.addPending = addPending;
    }

    /**
     * Opens the Post Office for {@code trip}. The Post to select is {@code selectSlug} if given and
     * present, else the first one whose range contains {@code currentDate}.
     */
    public void show(Window owner, Trip trip, LocalDate currentDate, String selectSlug) {
        this.trip = trip;
        try {
            entryDates = store.listEntryDates(trip.ref());
            for (LocalDate d : entryDates) {
                DiaryEntry e = store.loadEntry(trip.ref(), d);
                dayLabels.put(d, UiText.entryLabel(trip, d, e.title()));
            }
            posts.addAll(store.listPosts(trip.ref()));
        } catch (RuntimeException e) {
            statusSink.error("Could not load Posts: " + UiText.describe(e));
            return;
        }
        sortPosts();

        buildTable();
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, p) -> {
            if (p != null) load(p);
        });
        table.setPlaceholder(new Label("No posts yet. Add one below."));
        table.setPrefHeight(150);

        titleField.setPrefColumnCount(40);
        tagsField.setTooltip(new Tooltip("An existing WordPress tag is reused whatever its case (reisen finds Reisen);"
                + " a new one is created exactly as typed here"));
        tagsField.setPromptText("WordPress tags, comma-separated (sent with a new draft, optional on update)");
        introArea.setWrapText(true);
        introArea.setPrefRowCount(5);
        introArea.setPromptText("Optional lead paragraph (Markdown) shown above the days");
        StringConverter<LocalDate> dayConverter = new StringConverter<>() {
            @Override public String toString(LocalDate d) {
                return d == null ? "" : dayLabels.computeIfAbsent(d, x -> UiText.entryLabel(trip, x, null));
            }
            @Override public LocalDate fromString(String s) { return null; }
        };
        for (ComboBox<LocalDate> c : List.of(fromPicker, toPicker)) {
            c.getItems().setAll(entryDates);
            c.setConverter(dayConverter);
            c.setPrefWidth(420);
            c.setMaxWidth(420);
        }
        // Moving one end past the other drags the other along
        fromPicker.valueProperty().addListener((obs, old, d) -> {
            if (d != null && toPicker.getValue() != null && d.isAfter(toPicker.getValue())) toPicker.setValue(d);
        });
        toPicker.valueProperty().addListener((obs, old, d) -> {
            if (d != null && fromPicker.getValue() != null && d.isBefore(fromPicker.getValue())) fromPicker.setValue(d);
        });
        errorLabel.getStyleClass().add("sync-status-error");
        errorLabel.setWrapText(true);

        GridPane grid = Dialogs.formGrid();
        grid.addRow(0, new Label("Title:"), titleField);
        grid.addRow(1, new Label("From:"), fromPicker);
        grid.addRow(2, new Label("To:"), toPicker);
        grid.addRow(3, new Label("Tags:"), tagsField);
        grid.addRow(4, new Label("Intro:"), introArea);
        GridPane.setHgrow(tagsField, Priority.ALWAYS);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(introArea, Priority.ALWAYS);

        Button addBtn = new Button("➕ Add Post");
        addBtn.setOnAction(e -> startNew(currentDate));
        deleteBtn = new Button("🗑 Delete Post");
        deleteBtn.setOnAction(e -> delete());
        saveBtn = new Button("💾 Save Post");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> save());
        openWpBtn = new Button("🌐 Open in WordPress");
        openWpBtn.setOnAction(e -> openInWordPress());
        openWpBtn.setDisable(true);
        HBox actions = new HBox(8, addBtn, deleteBtn, saveBtn, openWpBtn);

        VBox content = new VBox(8, table, grid, errorLabel, actions);
        content.setPadding(new Insets(4));
        dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("📮 Post Office");
        dlg.setHeaderText(trip.name());
        dlg.setResizable(true);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().setPrefWidth(720);
        Dialogs.applyStylesheet(dlg.getDialogPane());
        // CANCEL_CLOSE keeps the window's X button working; edits are saved explicitly
        dlg.getDialogPane().getButtonTypes().setAll(new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE));

        Post initial = posts.stream().filter(p -> p.slug().equals(selectSlug)).findFirst()
                .or(() -> PostPicker.preselect(posts, currentDate)).orElse(null);
        if (initial != null) {
            table.getSelectionModel().select(initial);
            table.scrollTo(initial);
        } else {
            setEditorEnabled(false);
        }
        dlg.showAndWait();
    }

    private void buildTable() {
        TableColumn<Post, String> start = column("Start day", 90, p -> PostPicker.day(trip, p.from()));
        TableColumn<Post, String> end = column("End day", 90, p -> PostPicker.day(trip, p.to()));
        TableColumn<Post, String> title = column("Title", 330, p -> p.title());
        TableColumn<Post, String> status = column("Status", 160, p -> {
            String wp = p.wpId() == null || p.wpId().isBlank() ? "local" : "wp " + p.wpId();
            return p.datesIn(entryDates).isEmpty() ? "⚠ no entries · " + wp : wp;
        });
        table.getColumns().setAll(List.of(start, end, title, status));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getItems().setAll(posts);
    }

    private TableColumn<Post, String> column(String name, double width, java.util.function.Function<Post, String> value) {
        TableColumn<Post, String> c = new TableColumn<>(name);
        c.setPrefWidth(width);
        c.setSortable(false);
        c.setCellValueFactory(cd -> new SimpleStringProperty(value.apply(cd.getValue())));
        return c;
    }

    private void load(Post post) {
        creating = false;
        editing = post;
        setEditorEnabled(true);
        titleField.setText(post.title());
        for (ComboBox<LocalDate> c : List.of(fromPicker, toPicker)) {
            // A stored date without an entry (yet) stays selectable, so loading never alters the Post
            List<LocalDate> items = new ArrayList<>(entryDates);
            for (LocalDate d : List.of(post.from(), post.to())) if (!items.contains(d)) items.add(d);
            items.sort(Comparator.naturalOrder());
            c.getItems().setAll(items);
        }
        fromPicker.setValue(post.from());
        toPicker.setValue(post.to());
        tagsField.setText(String.join(", ", post.tags()));
        introArea.setText(post.intro() == null ? "" : post.intro().strip());
        errorLabel.setText("");
        deleteBtn.setDisable(false);
        openWpBtn.setDisable(!hasWpId(post));
    }

    private static boolean hasWpId(Post p) {
        return p != null && p.wpId() != null && !p.wpId().isBlank();
    }

    private void openInWordPress() {
        if (!hasWpId(editing)) return;
        String site = settingsStore.load().getWordPress().getSite();
        if (site == null || site.isBlank()) {
            errorLabel.setText("No WordPress site configured in Settings");
            return;
        }
        browser.open(PostPicker.editorUrl(site, editing.wpId()));
    }

    private void startNew(LocalDate currentDate) {
        table.getSelectionModel().clearSelection();
        creating = true;
        editing = null;
        setEditorEnabled(true);
        LocalDate day = currentDate != null ? currentDate
                : entryDates.isEmpty() ? trip.startDate() : entryDates.getFirst();
        LocalDate shown = entryDates.contains(day) ? day : entryDates.isEmpty() ? null : entryDates.getFirst();
        titleField.setText(shown == null ? trip.name() : PostPicker.defaultTitle(trip, shown, shown));
        for (ComboBox<LocalDate> c : List.of(fromPicker, toPicker)) c.getItems().setAll(entryDates);
        LocalDate start = entryDates.contains(day) ? day : entryDates.isEmpty() ? null : entryDates.getFirst();
        fromPicker.setValue(start);
        toPicker.setValue(start);
        tagsField.setText("");
        introArea.setText("");
        errorLabel.setText("");
        deleteBtn.setDisable(true);
        openWpBtn.setDisable(true);
        titleField.requestFocus();
        titleField.selectAll();
    }

    private void setEditorEnabled(boolean enabled) {
        for (var n : List.of(titleField, fromPicker, toPicker, tagsField, introArea, saveBtn, deleteBtn)) {
            if (n != null) n.setDisable(!enabled);
        }
        if (!enabled) {
            titleField.clear();
            fromPicker.setValue(null);
            toPicker.setValue(null);
            tagsField.clear();
            introArea.clear();
        }
    }

    private void save() {
        String title = titleField.getText() == null ? "" : titleField.getText().strip();
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (title.isEmpty()) { errorLabel.setText("A title is required"); return; }
        if (from == null || to == null) { errorLabel.setText("From and To are required"); return; }
        if (to.isBefore(from)) { errorLabel.setText("To must not be before From"); return; }
        String intro = introArea.getText() == null ? "" : introArea.getText().strip();
        List<String> tags = Arrays.stream((tagsField.getText() == null ? "" : tagsField.getText()).split(","))
                .map(String::strip).filter(t -> !t.isEmpty()).distinct().toList();
        Post saved;
        if (creating || editing == null) {
            String slug = PostPicker.uniqueSlug(title, posts.stream().map(Post::slug).toList());
            saved = Post.of(slug, title, intro, from, to).withTags(tags);
        } else {
            saved = new Post(editing.slug(), title, intro, from, to, editing.wpId(), editing.wpModified(), tags);
        }
        try {
            store.savePost(trip.ref(), saved);
        } catch (RuntimeException e) {
            errorLabel.setText("Saving failed: " + UiText.describe(e));
            return;
        }
        addPending.accept(trip.ref().path() + "/posts/" + saved.slug(), creating ? "Create" : "Update");
        if (editing != null) posts.remove(editing);
        posts.add(saved);
        sortPosts();
        table.getItems().setAll(posts);
        table.getSelectionModel().select(saved);
        load(saved);
        statusSink.status("Saved post " + saved.slug());
    }

    private void delete() {
        if (editing == null) return;
        boolean wp = editing.wpId() != null && !editing.wpId().isBlank();
        String message = "Delete the post \"" + editing.title() + "\"? The days themselves are kept."
                + (wp ? "\n\nThis post is connected to WordPress (" + editing.wpId()
                + "): the post there is NOT deleted, you have to clean it up on WordPress yourself." : "");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.initOwner(dlg.getDialogPane().getScene().getWindow());
        alert.setHeaderText("Delete post");
        Dialogs.applyStylesheet(alert.getDialogPane());
        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.YES) return;
        try {
            store.deletePost(trip.ref(), editing.slug());
        } catch (RuntimeException e) {
            errorLabel.setText("Deleting failed: " + UiText.describe(e));
            return;
        }
        addPending.accept(trip.ref().path() + "/posts/" + editing.slug(), "Delete");
        statusSink.status("Deleted post " + editing.slug());
        posts.remove(editing);
        editing = null;
        table.getItems().setAll(posts);
        table.getSelectionModel().clearSelection();
        setEditorEnabled(false);
    }

    private void sortPosts() {
        posts.sort(Comparator.comparing(Post::from).thenComparing(Post::to).thenComparing(Post::slug));
    }
}
