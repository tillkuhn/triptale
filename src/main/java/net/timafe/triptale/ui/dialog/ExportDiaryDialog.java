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
import javafx.scene.text.Font;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.export.DiaryExporter;
import net.timafe.triptale.export.ExportTempFiles;
import net.timafe.triptale.export.SocialMediaExporter;
import net.timafe.triptale.export.WordPressExporter;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Clipboards;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.StatusSink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Shows the export of a range of a trip's entries (the whole trip from the Trip menu, a single
 * day from Export Tale) as Markdown, WordPress block markup (todo 44) or a flattened plain-text
 * Social Media summary (todo 67), with actions to copy it or preview it in the system browser. In
 * WordPress format, "Post to WordPress" opens {@link WordPressPublishDialog} for the range
 * (todo 62). The image source defaults to the richest one that has images (todo 47); Social
 * Media has no images at all, it is plain text only.
 */
public final class ExportDiaryDialog {

    private enum Format {
        MARKDOWN("Markdown"),
        WORDPRESS("WordPress"),
        SOCIAL_MEDIA("Social Media");

        final String label;
        Format(String label) { this.label = label; }
    }

    /** The image choices; the exporter has no ad-hoc folder source, it only renders content. */
    private enum Images {
        NONE("None", null),
        PHOTO_LIB("🗂 " + ImpressionSource.PHOTO_LIBRARY.label(), ImpressionSource.PHOTO_LIBRARY),
        ATTACHMENTS("🎒 " + ImpressionSource.TRIP_ATTACHMENTS.label(), ImpressionSource.TRIP_ATTACHMENTS);

        final String label;
        final ImpressionSource source;
        Images(String label, ImpressionSource source) { this.label = label; this.source = source; }
    }

    private final DiaryExporter diaryExporter;
    private final WordPressExporter wordPressExporter;
    private final SocialMediaExporter socialMediaExporter;
    private final MarkdownStore store;
    private final SettingsStore settingsStore;
    private final ImpressionsService impressions;
    private final ExportTempFiles tempFiles;
    private final BrowserLauncher browser;
    private final StatusSink statusSink;
    private final BiConsumer<String, String> addPending;

    public ExportDiaryDialog(DiaryExporter diaryExporter, WordPressExporter wordPressExporter,
                             SocialMediaExporter socialMediaExporter, MarkdownStore store,
                             SettingsStore settingsStore, ImpressionsService impressions, ExportTempFiles tempFiles,
                             BrowserLauncher browser, StatusSink statusSink, BiConsumer<String, String> addPending) {
        this.diaryExporter = diaryExporter;
        this.wordPressExporter = wordPressExporter;
        this.socialMediaExporter = socialMediaExporter;
        this.store = store;
        this.settingsStore = settingsStore;
        this.impressions = impressions;
        this.tempFiles = tempFiles;
        this.browser = browser;
        this.statusSink = statusSink;
        this.addPending = addPending;
    }

    /** Exports the whole trip. */
    public void show(Trip trip) {
        show(trip, null, null);
    }

    /** Exports the entries {@code from}..{@code to}; null means the first/last entry. */
    public void show(Trip trip, LocalDate from, LocalDate to) {
        EntryRangePicker range;
        List<LocalDate> entryDates;
        try {
            List<DiaryEntry> entries = store.listEntryDates(trip.ref()).stream()
                    .map(d -> store.loadEntry(trip.ref(), d))
                    .toList();
            range = new EntryRangePicker(trip, entries, from, to);
            entryDates = entries.stream().map(DiaryEntry::date).toList();
        } catch (RuntimeException e) {
            statusSink.error("Export failed: " + e.getMessage());
            return;
        }

        TextArea ta = new TextArea();
        ta.setEditable(false);
        ta.setWrapText(true);
        // The generic 'monospace' is a hairline typewriter face on macOS; Menlo reads much darker
        ta.setStyle("-fx-font-family: '" + (Font.getFamilies().contains("Menlo") ? "Menlo" : "monospace") + "';");
        ta.getStyleClass().add("export-text");
        ta.setPrefRowCount(28);
        ta.setPrefColumnCount(90);

        ComboBox<Images> imagesCombo = new ComboBox<>();
        imagesCombo.getItems().setAll(Images.values());
        imagesCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Images i) { return i == null ? "" : i.label; }
            @Override public Images fromString(String s) { return null; }
        });
        imagesCombo.setValue(defaultImages(trip, range.from(), range.to()));
        CheckBox favesCheck = new CheckBox("Faves only");
        if (impressions.faveFilter().isEmpty()) {
            favesCheck.setText("Faves only (no pattern defined)");
            favesCheck.setDisable(true);
        } else {
            favesCheck.setTooltip(new Tooltip("Fave filter: " + impressions.faveFilter()));
            favesCheck.disableProperty().bind(imagesCombo.valueProperty().isEqualTo(Images.NONE));
        }

        ComboBox<Format> formatCombo = new ComboBox<>();
        formatCombo.getItems().setAll(Format.values());
        formatCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Format f) { return f == null ? "" : f.label; }
            @Override public Format fromString(String s) { return null; }
        });
        formatCombo.setValue(Format.MARKDOWN);
        formatCombo.setTooltip(new Tooltip("WordPress: block markup for the block editor's Code editor"
                + " · Social Media: plain-text paragraphs, no facts, links or images"));

        Runnable render = () -> {
            try {
                ta.setText(switch (formatCombo.getValue()) {
                    case WORDPRESS -> wordPressExporter.export(trip, range.from(), range.to(),
                            imagesCombo.getValue() == Images.ATTACHMENTS, favesCheck.isSelected());
                    case SOCIAL_MEDIA -> socialMediaExporter.export(trip, range.from(), range.to());
                    case MARKDOWN -> diaryExporter.exportTrip(trip, range.from(), range.to());
                });
            } catch (RuntimeException e) {
                statusSink.error("Export failed: " + e.getMessage());
            }
        };
        range.onChange(render);
        formatCombo.valueProperty().addListener((obs, old, f) -> {
            offerImages(f, imagesCombo);
            render.run();
        });
        // Images only change the WordPress text; Markdown has no image combo use and Social Media
        // has no images at all
        imagesCombo.valueProperty().addListener((obs, old, i) -> {
            if (formatCombo.getValue() == Format.WORDPRESS) render.run();
        });
        favesCheck.selectedProperty().addListener((obs, old, sel) -> {
            if (formatCombo.getValue() == Format.WORDPRESS) render.run();
        });
        render.run();

        HBox imagesBox = new HBox(8, new Label("Format:"), formatCombo, new Label("Images:"), imagesCombo, favesCheck);
        imagesBox.setAlignment(Pos.CENTER_LEFT);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("📖 Diary Composer");
        dlg.setHeaderText(trip.name());
        dlg.setResizable(true);
        dlg.getDialogPane().setContent(new VBox(8, range.node(), ta, imagesBox));
        Dialogs.applyStylesheet(dlg.getDialogPane());

        ButtonType copyType = new ButtonType("📋 Clipboard", ButtonBar.ButtonData.OTHER);
        ButtonType previewType = new ButtonType("👁 Browser Preview", ButtonBar.ButtonData.OTHER);
        ButtonType sendType = new ButtonType("📡 Post to WordPress", ButtonBar.ButtonData.OTHER);
        ButtonType closeType = new ButtonType("❌ Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().setAll(copyType, previewType, sendType, closeType);

        // Event filters with consume() keep the dialog open — these are repeatable actions,
        // not dialog results.
        Button copyBtn = (Button) dlg.getDialogPane().lookupButton(copyType);
        copyBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            Clipboards.putString(ta.getText());
            statusSink.status(switch (formatCombo.getValue()) {
                case WORDPRESS -> "WordPress markup copied: paste it into the block editor's Code editor";
                case SOCIAL_MEDIA -> "Social Media text copied to clipboard";
                case MARKDOWN -> "Diary copied to clipboard";
            });
            ev.consume();
        });

        Button previewBtn = (Button) dlg.getDialogPane().lookupButton(previewType);
        previewBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            switch (formatCombo.getValue()) {
                case WORDPRESS -> openInBrowser(wordPressExporter.previewHtml(trip.name(), ta.getText()));
                case SOCIAL_MEDIA -> openInBrowser(socialMediaExporter.previewHtml(trip.name(), ta.getText()));
                case MARKDOWN -> previewInBrowser(trip, range.from(), range.to(),
                        imagesCombo.getValue().source, favesCheck.isSelected());
            }
            ev.consume();
        });

        Button sendBtn = (Button) dlg.getDialogPane().lookupButton(sendType);
        sendBtn.disableProperty().bind(formatCombo.valueProperty().isNotEqualTo(Format.WORDPRESS));
        sendBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume();
            AppSettings.WordPress wp = settingsStore.load().getWordPress();
            List<LocalDate> dates = entryDates.stream()
                    .filter(d -> (range.from() == null || !d.isBefore(range.from()))
                            && (range.to() == null || !d.isAfter(range.to())))
                    .toList();
            if (wp.getAccessToken().isBlank()) {
                Dialogs.showInfo("📡 Post to WordPress", "Not connected to WordPress",
                        new Label("Connect WordPress in ⚙ Edit Settings first."));
            } else if (dates.isEmpty()) {
                statusSink.error("No entries in the selected range");
            } else {
                new WordPressPublishDialog(wordPressExporter, store, wp, browser, statusSink, addPending)
                        .show(dlg.getDialogPane().getScene().getWindow(), trip, dates,
                                imagesCombo.getValue() == Images.ATTACHMENTS, favesCheck.isSelected());
            }
        });


        dlg.showAndWait();
    }

    private void previewInBrowser(Trip trip, LocalDate from, LocalDate to, ImpressionSource images, boolean favesOnly) {
        try {
            openInBrowser(diaryExporter.exportTripAsHtml(trip, from, to, images, favesOnly));
        } catch (RuntimeException ex) {
            statusSink.error("Preview failed: " + ex.getMessage());
        }
    }

    private void openInBrowser(String html) {
        try {
            Path tmp = tempFiles.newFile("export-", ".html");
            Files.writeString(tmp, html, StandardCharsets.UTF_8);
            browser.open(tmp.toUri().toString());
        } catch (IOException | RuntimeException ex) {
            statusSink.error("Preview failed: " + ex.getMessage());
        }
    }

    /**
     * WordPress can only hotlink backpack images, and only with a public base URL in Settings;
     * Markdown offers every source; Social Media is plain text and has no images at all. Keeps
     * the current choice where it is still offered.
     */
    private void offerImages(Format format, ComboBox<Images> imagesCombo) {
        Images current = imagesCombo.getValue();
        if (format == Format.SOCIAL_MEDIA) {
            imagesCombo.getItems().setAll(Images.NONE);
            imagesCombo.setTooltip(new Tooltip("Social Media is plain text only, no images"));
            imagesCombo.setValue(Images.NONE);
        } else if (format == Format.WORDPRESS) {
            boolean available = wordPressExporter.imagesAvailable();
            imagesCombo.getItems().setAll(available ? List.of(Images.NONE, Images.ATTACHMENTS) : List.of(Images.NONE));
            imagesCombo.setTooltip(new Tooltip(available
                    ? "Images are hotlinked from the backpack's public URL; run Smart Sync first"
                    : "Set a Backpack public URL in Settings to include images"));
            imagesCombo.setValue(available && current != Images.NONE ? Images.ATTACHMENTS : Images.NONE);
        } else {
            imagesCombo.getItems().setAll(Images.values());
            imagesCombo.setTooltip(null);
            imagesCombo.setValue(current);
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
}
