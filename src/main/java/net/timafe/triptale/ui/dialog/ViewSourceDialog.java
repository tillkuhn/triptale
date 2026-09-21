package net.timafe.triptale.ui.dialog;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import net.timafe.triptale.domain.TripRef;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.ui.Clipboards;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.StatusSink;

import java.time.LocalDate;

/** Raw Markdown of one entry, with the data-dir-relative path in the header. */
public final class ViewSourceDialog {

    private final MarkdownStore store;
    private final StatusSink statusSink;

    public ViewSourceDialog(MarkdownStore store, StatusSink statusSink) {
        this.store = store;
        this.statusSink = statusSink;
    }

    public void show(TripRef ref, LocalDate date) {
        String source;
        try {
            source = store.readEntrySource(ref, date);
        } catch (RuntimeException e) {
            statusSink.error("Failed to read source: " + e.getMessage());
            return;
        }

        TextArea ta = new TextArea(source);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setStyle("-fx-font-family: 'monospace';");
        ta.setPrefRowCount(28);
        ta.setPrefColumnCount(70);

        var fullPath = store.entryFile(ref, date);
        Label pathLabel = new Label("File: " + store.dataDir().relativize(fullPath));
        Button copyPathBtn = new Button("📋");
        copyPathBtn.setTooltip(new Tooltip("Copy full path to clipboard"));
        copyPathBtn.setOnAction(ev -> {
            Clipboards.putString(fullPath.toString());
            statusSink.status("Full path copied to clipboard");
        });
        HBox header = new HBox(10, pathLabel, copyPathBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10));
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("View Source");
        dlg.getDialogPane().setHeader(header);
        dlg.setResizable(true);
        dlg.getDialogPane().setContent(ta);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        ButtonType copyType = new ButtonType("Copy", ButtonBar.ButtonData.LEFT);
        dlg.getDialogPane().getButtonTypes().setAll(copyType, ButtonType.CLOSE);

        Button copyBtn = (Button) dlg.getDialogPane().lookupButton(copyType);
        copyBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            Clipboards.putString(source);
            statusSink.status("Source copied to clipboard");
            ev.consume();
        });

        dlg.showAndWait();
    }
}
