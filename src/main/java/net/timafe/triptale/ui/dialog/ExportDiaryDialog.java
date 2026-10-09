package net.timafe.triptale.ui.dialog;

import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.export.DiaryExporter;
import net.timafe.triptale.export.ExportTempFiles;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Clipboards;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.StatusSink;
import net.timafe.triptale.ui.UiText;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows the Markdown export of a range of a trip's entries (the whole trip from the Trip menu,
 * a single day from Export Tale), with actions to copy it or render it as HTML and open that in
 * the system browser. The image source defaults to the richest one that has images (todo 47).
 */
public final class ExportDiaryDialog {

    /** The image choices; the exporter has no ad-hoc folder source, it only renders content. */
    private enum Images {
        NONE("None", null),
        PHOTO_LIB(ImpressionSource.PHOTO_LIBRARY.label(), ImpressionSource.PHOTO_LIBRARY),
        ATTACHMENTS(ImpressionSource.TRIP_ATTACHMENTS.label(), ImpressionSource.TRIP_ATTACHMENTS);

        final String label;
        final ImpressionSource source;
        Images(String label, ImpressionSource source) { this.label = label; this.source = source; }
    }

    private final DiaryExporter diaryExporter;
    private final MarkdownStore store;
    private final ImpressionsService impressions;
    private final ExportTempFiles tempFiles;
    private final BrowserLauncher browser;
    private final StatusSink statusSink;

    public ExportDiaryDialog(DiaryExporter diaryExporter, MarkdownStore store, ImpressionsService impressions,
                             ExportTempFiles tempFiles, BrowserLauncher browser, StatusSink statusSink) {
        this.diaryExporter = diaryExporter;
        this.store = store;
        this.impressions = impressions;
        this.tempFiles = tempFiles;
        this.browser = browser;
        this.statusSink = statusSink;
    }

    /** Exports the whole trip. */
    public void show(Trip trip) {
        show(trip, null, null);
    }

    /** Exports the entries {@code from}..{@code to}; null means the first/last entry. */
    public void show(Trip trip, LocalDate from, LocalDate to) {
        List<LocalDate> dates;
        Map<LocalDate, String> labels = new HashMap<>();
        try {
            dates = store.listEntryDates(trip.ref());
            for (LocalDate d : dates) labels.put(d, entryLabel(trip, store.loadEntry(trip.ref(), d)));
        } catch (RuntimeException e) {
            statusSink.error("Export failed: " + e.getMessage());
            return;
        }
        StringConverter<LocalDate> converter = new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : labels.getOrDefault(d, d.toString()); }
            @Override public LocalDate fromString(String s) { return null; }
        };
        ComboBox<LocalDate> fromCombo = new ComboBox<>();
        ComboBox<LocalDate> toCombo = new ComboBox<>();
        for (ComboBox<LocalDate> c : List.of(fromCombo, toCombo)) {
            c.getItems().setAll(dates);
            c.setConverter(converter);
            c.setDisable(dates.isEmpty());
        }
        if (!dates.isEmpty()) {
            fromCombo.setValue(from != null && dates.contains(from) ? from : dates.getFirst());
            toCombo.setValue(to != null && dates.contains(to) ? to : dates.getLast());
        }

        TextArea ta = new TextArea();
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.setStyle("-fx-font-family: 'monospace';");
        ta.setPrefRowCount(28);
        ta.setPrefColumnCount(90);
        Runnable render = () -> {
            try {
                ta.setText(diaryExporter.exportTrip(trip, fromCombo.getValue(), toCombo.getValue()));
            } catch (RuntimeException e) {
                statusSink.error("Export failed: " + e.getMessage());
            }
        };
        // Keep From <= To by dragging the other end along.
        fromCombo.valueProperty().addListener((obs, old, d) -> {
            if (d != null && toCombo.getValue() != null && d.isAfter(toCombo.getValue())) toCombo.setValue(d);
            render.run();
        });
        toCombo.valueProperty().addListener((obs, old, d) -> {
            if (d != null && fromCombo.getValue() != null && d.isBefore(fromCombo.getValue())) fromCombo.setValue(d);
            render.run();
        });
        render.run();

        HBox rangeBox = new HBox(8, new Label("From:"), fromCombo, new Label("To:"), toCombo);
        rangeBox.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Images> imagesCombo = new ComboBox<>();
        imagesCombo.getItems().setAll(Images.values());
        imagesCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Images i) { return i == null ? "" : i.label; }
            @Override public Images fromString(String s) { return null; }
        });
        imagesCombo.setValue(defaultImages(trip, fromCombo.getValue(), toCombo.getValue()));
        CheckBox favesCheck = new CheckBox("Faves only");
        if (impressions.faveFilter().isEmpty()) {
            favesCheck.setText("Faves only (no pattern defined)");
            favesCheck.setDisable(true);
        } else {
            favesCheck.setTooltip(new Tooltip("Fave filter: " + impressions.faveFilter()));
            favesCheck.disableProperty().bind(imagesCombo.valueProperty().isEqualTo(Images.NONE));
        }

        HBox imagesBox = new HBox(8, new Label("Images:"), imagesCombo, favesCheck);
        imagesBox.setAlignment(Pos.CENTER_LEFT);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Export Diary");
        dlg.setHeaderText(trip.name());
        dlg.setResizable(true);
        dlg.getDialogPane().setContent(new VBox(8, rangeBox, ta, imagesBox));
        Dialogs.applyStylesheet(dlg.getDialogPane());

        ButtonType copyType = new ButtonType("Copy", ButtonBar.ButtonData.OTHER);
        ButtonType previewType = new ButtonType("Preview in Browser", ButtonBar.ButtonData.OTHER);
        dlg.getDialogPane().getButtonTypes().setAll(copyType, previewType, ButtonType.CLOSE);

        // Event filters with consume() keep the dialog open — these are repeatable actions,
        // not dialog results.
        Button copyBtn = (Button) dlg.getDialogPane().lookupButton(copyType);
        copyBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            Clipboards.putString(ta.getText());
            statusSink.status("Diary copied to clipboard");
            ev.consume();
        });

        Button previewBtn = (Button) dlg.getDialogPane().lookupButton(previewType);
        previewBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            previewInBrowser(trip, fromCombo.getValue(), toCombo.getValue(),
                    imagesCombo.getValue().source, favesCheck.isSelected());
            ev.consume();
        });

        dlg.showAndWait();
    }

    private void previewInBrowser(Trip trip, LocalDate from, LocalDate to, ImpressionSource images, boolean favesOnly) {
        try {
            String html = diaryExporter.exportTripAsHtml(trip, from, to, images, favesOnly);
            Path tmp = tempFiles.newFile("export-", ".html");
            Files.writeString(tmp, html, StandardCharsets.UTF_8);
            browser.open(tmp.toUri().toString());
        } catch (IOException | RuntimeException ex) {
            statusSink.error("Preview failed: " + ex.getMessage());
        }
    }

    /** Attachments if any day in range has image attachments, else the photo library if configured. */
    private Images defaultImages(Trip trip, LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (!impressions.images(ImpressionSource.TRIP_ATTACHMENTS, trip, d).isEmpty()) {
                    return Images.ATTACHMENTS;
                }
            }
        }
        return impressions.photoLibraryConfigured() ? Images.PHOTO_LIB : Images.NONE;
    }

    /** "Day 2: Frankfurt to Mannheim"; the date stands in for a missing day number or title. */
    private static String entryLabel(Trip trip, DiaryEntry e) {
        Long day = trip.dayNumber(e.date());
        String title = e.title() == null || e.title().isBlank() || e.title().equals(DiaryEntry.DEFAULT_TITLE)
                ? UiText.friendlyDate(e.date()) : e.title();
        return (day == null ? e.date().toString() : "Day " + day) + ": " + title;
    }
}
