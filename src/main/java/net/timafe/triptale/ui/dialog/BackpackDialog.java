package net.timafe.triptale.ui.dialog;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.util.ByteSizes;
import net.timafe.triptale.attachments.BackpackFile;
import net.timafe.triptale.attachments.FileKind;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Clipboards;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.RecentFolder;
import net.timafe.triptale.ui.StatusSink;
import net.timafe.triptale.ui.UiText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The day's backpack (todo 56): an explorer-style table of the files in the day's attachment
 * folder (name, kind, size, changed) with Add, Open, Reveal in Finder, Copy Path and Delete.
 *
 * <p>Like {@link InboxDialog}, it acts on the files directly instead of returning a result to
 * apply — there is no controller state to change, only the folder. {@link #show} reports whether
 * anything was added or deleted, so the caller can refresh the counts on the main window.
 */
public final class BackpackDialog {

    private static final Logger log = LoggerFactory.getLogger(BackpackDialog.class);
    private static final DateTimeFormatter CHANGED =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final AttachmentsDir attachmentsDir;
    private final BrowserLauncher browser;
    private final StatusSink statusSink;
    private final RecentFolder recentFolder;

    public BackpackDialog(AttachmentsDir attachmentsDir, BrowserLauncher browser, StatusSink statusSink,
                          RecentFolder recentFolder) {
        this.attachmentsDir = attachmentsDir;
        this.browser = browser;
        this.statusSink = statusSink;
        this.recentFolder = recentFolder;
    }

    /**
     * Shows the day's backpack; {@code title} is the entry title as currently shown in the main
     * window (may be unsaved or blank). Returns true if files were added or deleted.
     */
    public boolean show(Trip trip, LocalDate date, String title) {
        Dialog<Void> dialog = new Dialog<>();
        // No header band: the label goes into the title bar, which the OS truncates itself.
        dialog.setTitle("🎒 Your backpack for " + UiText.entryLabel(trip, date, title));

        ObservableList<BackpackFile> rows = FXCollections.observableArrayList();
        TableView<BackpackFile> table = new TableView<>(rows);
        table.setPlaceholder(new Label("Nothing packed for this day yet"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<BackpackFile, String> nameCol = column("Name", BackpackFile::name, String::toString);
        nameCol.setComparator(String.CASE_INSENSITIVE_ORDER);
        nameCol.setPrefWidth(260);
        TableColumn<BackpackFile, FileKind> kindCol = column("Kind", BackpackFile::kind, FileKind::display);
        kindCol.setPrefWidth(90);
        TableColumn<BackpackFile, Long> sizeCol = column("Size", BackpackFile::size, ByteSizes::humanize);
        sizeCol.setStyle("-fx-alignment: center-right;");
        sizeCol.setPrefWidth(80);
        TableColumn<BackpackFile, Instant> changedCol = column("Changed", BackpackFile::modified, CHANGED::format);
        changedCol.setPrefWidth(130);
        table.getColumns().setAll(List.of(nameCol, kindCol, sizeCol, changedCol));
        table.getSortOrder().add(nameCol);

        boolean[] changed = {false};
        Runnable reload = () -> {
            try {
                rows.setAll(attachmentsDir.dayFiles(trip.ref(), date));
                table.sort();
            } catch (RuntimeException e) {
                statusSink.error(UiText.describe(e));
            }
        };
        reload.run();

        Button add = new Button("+ Add…");
        add.setTooltip(new Tooltip("Copy files into this day's backpack (same name replaces)"));
        Button open = new Button("Open");
        open.setTooltip(new Tooltip("Open with the default app (double-click, Enter)"));
        Button reveal = new Button("Reveal in Finder");
        Button copyPath = new Button("Copy Path");
        Button delete = new Button("🗑 Delete");
        delete.setTooltip(new Tooltip("Delete this file (⌫); a cloud sync removes it from the bucket too"));
        for (Button b : List.of(open, reveal, copyPath, delete)) {
            b.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        }

        add.setOnAction(e -> {
            if (addFiles(trip, date, add)) {
                changed[0] = true;
                reload.run();
            }
        });
        open.setOnAction(e -> openFile(table.getSelectionModel().getSelectedItem()));
        reveal.setOnAction(e -> reveal(table.getSelectionModel().getSelectedItem()));
        copyPath.setOnAction(e -> {
            BackpackFile f = table.getSelectionModel().getSelectedItem();
            if (f == null) return;
            Clipboards.putString(f.path().toString());
            statusSink.status("Copied " + UiText.homeRelative(f.path()));
        });
        delete.setOnAction(e -> {
            if (delete(trip, date, table.getSelectionModel().getSelectedItem())) {
                changed[0] = true;
                reload.run();
            }
        });

        table.setRowFactory(tv -> {
            TableRow<BackpackFile> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2 && !row.isEmpty()) {
                    openFile(row.getItem());
                }
            });
            return row;
        });
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                openFile(table.getSelectionModel().getSelectedItem());
                ev.consume();
            } else if (ev.getCode() == KeyCode.DELETE || ev.getCode() == KeyCode.BACK_SPACE) {
                delete.fire();
                ev.consume();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, add, spacer, open, reveal, copyPath, delete);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, table, actions);
        content.setPadding(new Insets(4));
        VBox.setVgrow(table, Priority.ALWAYS);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(640);
        dialog.getDialogPane().setPrefHeight(420);
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Dialogs.applyStylesheet(dialog.getDialogPane());
        dialog.setOnShown(e -> {
            if (rows.isEmpty()) add.requestFocus(); else table.requestFocus();
        });

        dialog.showAndWait();
        return changed[0];
    }

    private static <T> TableColumn<BackpackFile, T> column(String name, Function<BackpackFile, T> value,
                                                          Function<T, String> text) {
        TableColumn<BackpackFile, T> col = new TableColumn<>(name);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(value.apply(c.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : text.apply(item));
            }
        });
        return col;
    }

    private boolean addFiles(Trip trip, LocalDate date, Button owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add to Backpack for " + UiText.friendlyDate(date));
        chooser.setInitialDirectory(recentFolder.initial());
        List<File> files = chooser.showOpenMultipleDialog(owner.getScene().getWindow());
        if (files == null || files.isEmpty()) return false;
        recentFolder.remember(files.getFirst().getParentFile());
        try {
            List<Path> copies = attachmentsDir.addFiles(trip.ref(), date, files.stream().map(File::toPath).toList());
            statusSink.status("Added " + copies.size() + " file" + (copies.size() == 1 ? "" : "s") + " to "
                    + UiText.homeRelative(copies.getFirst().getParent()));
            return true;
        } catch (RuntimeException e) {
            statusSink.error(UiText.describe(e));
            return false;
        }
    }

    private boolean delete(Trip trip, LocalDate date, BackpackFile file) {
        if (file == null) return false;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + file.name() + "\" (" + ByteSizes.humanize(file.size()) + ") from this day's backpack?"
                        + "\n\nThis cannot be undone. With cloud sync, the next sync removes it from the bucket too.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("🎒 Backpack");
        confirm.setHeaderText("Delete File");
        Dialogs.applyStylesheet(confirm.getDialogPane());
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return false;
        try {
            attachmentsDir.deleteFile(trip.ref(), date, file.name());
            statusSink.status("Deleted " + UiText.homeRelative(file.path()));
            return true;
        } catch (RuntimeException e) {
            statusSink.error(UiText.describe(e));
            return false;
        }
    }

    private void openFile(BackpackFile file) {
        if (file != null) browser.open(file.path().toUri().toString());
    }

    /** macOS selects the file in Finder; elsewhere the day folder opens. */
    private void reveal(BackpackFile file) {
        if (file == null) return;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")) {
            try {
                new ProcessBuilder("open", "-R", file.path().toString()).start();
                return;
            } catch (IOException e) {
                log.warn("Could not reveal {}: {}", file.path(), e.getMessage());
            }
        }
        browser.open(file.path().getParent().toUri().toString());
    }
}
