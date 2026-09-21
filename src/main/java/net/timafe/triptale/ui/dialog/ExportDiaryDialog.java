package net.timafe.triptale.ui.dialog;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.export.DiaryExporter;
import net.timafe.triptale.export.ExportTempFiles;
import net.timafe.triptale.export.ImpressionsMode;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Clipboards;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.StatusSink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shows the Markdown export of a whole trip, with actions to copy it or render it as HTML and
 * open that in the system browser. The image mode defaults to the richest source the user has
 * actually configured a file pattern for.
 */
public final class ExportDiaryDialog {

    private final DiaryExporter diaryExporter;
    private final SettingsStore settingsStore;
    private final ExportTempFiles tempFiles;
    private final BrowserLauncher browser;
    private final StatusSink statusSink;

    public ExportDiaryDialog(DiaryExporter diaryExporter, SettingsStore settingsStore,
                             ExportTempFiles tempFiles, BrowserLauncher browser,
                             StatusSink statusSink) {
        this.diaryExporter = diaryExporter;
        this.settingsStore = settingsStore;
        this.tempFiles = tempFiles;
        this.browser = browser;
        this.statusSink = statusSink;
    }

    public void show(Trip trip) {
        String exported;
        try {
            exported = diaryExporter.exportTrip(trip);
        } catch (RuntimeException e) {
            statusSink.error("Export failed: " + e.getMessage());
            return;
        }

        TextArea ta = new TextArea(exported);
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.setStyle("-fx-font-family: 'monospace';");
        ta.setPrefRowCount(28);
        ta.setPrefColumnCount(90);

        ComboBox<ImpressionsMode> impressionsCombo = new ComboBox<>(
                FXCollections.observableArrayList(ImpressionsMode.NONE, ImpressionsMode.FAVES, ImpressionsMode.ALL));
        impressionsCombo.setConverter(new StringConverter<>() {
            @Override public String toString(ImpressionsMode m) {
                if (m == null) return "";
                return switch (m) {
                    case NONE -> "None";
                    case FAVES -> "Fave Impressions";
                    case ALL -> "All Impressions";
                };
            }
            @Override public ImpressionsMode fromString(String s) { return null; }
        });
        impressionsCombo.setValue(defaultImpressionsMode());

        HBox impressionsBox = new HBox(8, new Label("Images:"), impressionsCombo);
        impressionsBox.setAlignment(Pos.CENTER_LEFT);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Export Diary");
        dlg.setHeaderText(trip.name());
        dlg.setResizable(true);
        dlg.getDialogPane().setContent(new VBox(8, ta, impressionsBox));
        Dialogs.applyStylesheet(dlg.getDialogPane());

        ButtonType copyType = new ButtonType("Copy", ButtonBar.ButtonData.OTHER);
        ButtonType previewType = new ButtonType("Preview in Browser", ButtonBar.ButtonData.OTHER);
        dlg.getDialogPane().getButtonTypes().setAll(copyType, previewType, ButtonType.CLOSE);

        // Event filters with consume() keep the dialog open — these are repeatable actions,
        // not dialog results.
        Button copyBtn = (Button) dlg.getDialogPane().lookupButton(copyType);
        copyBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            Clipboards.putString(exported);
            statusSink.status("Diary copied to clipboard");
            ev.consume();
        });

        Button previewBtn = (Button) dlg.getDialogPane().lookupButton(previewType);
        previewBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            previewInBrowser(trip, impressionsCombo.getValue());
            ev.consume();
        });

        dlg.showAndWait();
    }

    private void previewInBrowser(Trip trip, ImpressionsMode mode) {
        try {
            String html = diaryExporter.exportTripAsHtml(trip, mode);
            Path tmp = tempFiles.newFile("export-", ".html");
            Files.writeString(tmp, html, StandardCharsets.UTF_8);
            browser.open(tmp.toUri().toString());
        } catch (IOException ex) {
            statusSink.error("Preview failed: " + ex.getMessage());
        }
    }

    /** Richest mode the user has a pattern configured for, so the default preview isn't empty. */
    private ImpressionsMode defaultImpressionsMode() {
        AppSettings settings = settingsStore.load();
        if (!settings.getImpressionsFilePattern().isBlank()) return ImpressionsMode.ALL;
        if (!settings.getImpressionsFaveFilePattern().isBlank()) return ImpressionsMode.FAVES;
        return ImpressionsMode.NONE;
    }
}
