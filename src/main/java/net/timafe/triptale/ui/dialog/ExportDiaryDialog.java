package net.timafe.triptale.ui.dialog;

import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
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
import net.timafe.triptale.domain.Post;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.export.DiaryExporter;
import net.timafe.triptale.export.ExportTempFiles;
import net.timafe.triptale.export.WordPressExporter;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Clipboards;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.StatusSink;
import net.timafe.triptale.wordpress.WordPressApiClient;
import net.timafe.triptale.wordpress.WordPressException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Shows the export of a range of a trip's entries (the whole trip from the Trip menu, a single
 * day from Export Tale) or of a Post (todo 60: a saved, possibly gappy selection of days) as
 * Markdown or as WordPress block markup (todo 44), with actions to copy it or preview it in the
 * system browser. With a Post selected, WordPress format and a connected account, "Send to
 * WordPress" creates or updates that post directly via the REST API, following the send rules in
 * {@code docs/60_wordpress_api_draft.md} (create if no {@code wp_id}, else conflict-check then
 * update, never changing {@code status} on update). The image source defaults to the richest one
 * that has images (todo 47).
 */
public final class ExportDiaryDialog {

    private enum Format {
        MARKDOWN("Markdown"),
        WORDPRESS("WordPress");

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
    private final MarkdownStore store;
    private final SettingsStore settingsStore;
    private final ImpressionsService impressions;
    private final ExportTempFiles tempFiles;
    private final BrowserLauncher browser;
    private final StatusSink statusSink;
    private final BiConsumer<String, String> addPending;

    public ExportDiaryDialog(DiaryExporter diaryExporter, WordPressExporter wordPressExporter, MarkdownStore store,
                             SettingsStore settingsStore, ImpressionsService impressions, ExportTempFiles tempFiles,
                             BrowserLauncher browser, StatusSink statusSink, BiConsumer<String, String> addPending) {
        this.diaryExporter = diaryExporter;
        this.wordPressExporter = wordPressExporter;
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
        List<Post> posts;
        try {
            List<DiaryEntry> entries = store.listEntryDates(trip.ref()).stream()
                    .map(d -> store.loadEntry(trip.ref(), d))
                    .toList();
            range = new EntryRangePicker(trip, entries, from, to);
            posts = store.listPosts(trip.ref());
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

        // Source: "Date range" (null) or one of the trip's Posts (todo 60)
        ComboBox<Post> sourceCombo = new ComboBox<>();
        List<Post> sourceItems = new ArrayList<>();
        sourceItems.add(null);
        sourceItems.addAll(posts);
        sourceCombo.getItems().setAll(sourceItems);
        sourceCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Post p) { return p == null ? "📅 Date range" : "📰 " + p.title(); }
            @Override public Post fromString(String s) { return null; }
        });
        sourceCombo.setValue(null);
        sourceCombo.setVisible(!posts.isEmpty());
        sourceCombo.setManaged(!posts.isEmpty());
        Label sourceLabel = new Label("Source:");
        sourceLabel.setVisible(!posts.isEmpty());
        sourceLabel.setManaged(!posts.isEmpty());

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
        formatCombo.setTooltip(new Tooltip("WordPress: block markup for the block editor's Code editor"));

        Runnable render = () -> {
            try {
                Post post = sourceCombo.getValue();
                if (post != null) {
                    ta.setText(formatCombo.getValue() == Format.WORDPRESS
                            ? wordPressExporter.exportPost(trip, post,
                                    imagesCombo.getValue() == Images.ATTACHMENTS, favesCheck.isSelected())
                            : diaryExporter.exportPost(trip, post));
                } else {
                    ta.setText(formatCombo.getValue() == Format.WORDPRESS
                            ? wordPressExporter.export(trip, range.from(), range.to(),
                                    imagesCombo.getValue() == Images.ATTACHMENTS, favesCheck.isSelected())
                            : diaryExporter.exportTrip(trip, range.from(), range.to()));
                }
            } catch (RuntimeException e) {
                statusSink.error("Export failed: " + e.getMessage());
            }
        };
        range.onChange(render);
        sourceCombo.valueProperty().addListener((obs, old, p) -> {
            range.node().setVisible(p == null);
            range.node().setManaged(p == null);
            render.run();
        });
        formatCombo.valueProperty().addListener((obs, old, f) -> {
            offerImages(f, imagesCombo);
            render.run();
        });
        // Images only change the WordPress text; the Markdown export has no images
        imagesCombo.valueProperty().addListener((obs, old, i) -> {
            if (formatCombo.getValue() == Format.WORDPRESS) render.run();
        });
        favesCheck.selectedProperty().addListener((obs, old, sel) -> {
            if (formatCombo.getValue() == Format.WORDPRESS) render.run();
        });
        render.run();

        HBox imagesBox = new HBox(8, sourceLabel, sourceCombo,
                new Label("Format:"), formatCombo, new Label("Images:"), imagesCombo, favesCheck);
        imagesBox.setAlignment(Pos.CENTER_LEFT);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("📖 Diary Composer");
        dlg.setHeaderText(trip.name());
        dlg.setResizable(true);
        range.node().setVisible(sourceCombo.getValue() == null);
        range.node().setManaged(sourceCombo.getValue() == null);
        dlg.getDialogPane().setContent(new VBox(8, range.node(), ta, imagesBox));
        Dialogs.applyStylesheet(dlg.getDialogPane());

        ButtonType copyType = new ButtonType("Copy", ButtonBar.ButtonData.OTHER);
        ButtonType previewType = new ButtonType("Preview in Browser", ButtonBar.ButtonData.OTHER);
        ButtonType sendType = new ButtonType("📡 Send to WordPress", ButtonBar.ButtonData.OTHER);
        dlg.getDialogPane().getButtonTypes().setAll(copyType, previewType, sendType, ButtonType.CLOSE);

        // Event filters with consume() keep the dialog open — these are repeatable actions,
        // not dialog results.
        Button copyBtn = (Button) dlg.getDialogPane().lookupButton(copyType);
        copyBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            Clipboards.putString(ta.getText());
            statusSink.status(formatCombo.getValue() == Format.WORDPRESS
                    ? "WordPress markup copied: paste it into the block editor's Code editor"
                    : "Diary copied to clipboard");
            ev.consume();
        });

        Button previewBtn = (Button) dlg.getDialogPane().lookupButton(previewType);
        previewBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            Post post = sourceCombo.getValue();
            if (formatCombo.getValue() == Format.WORDPRESS) {
                openInBrowser(wordPressExporter.previewHtml(post != null ? post.title() : trip.name(), ta.getText()));
            } else if (post != null) {
                previewPostInBrowser(trip, post, imagesCombo.getValue().source, favesCheck.isSelected());
            } else {
                previewInBrowser(trip, range.from(), range.to(),
                        imagesCombo.getValue().source, favesCheck.isSelected());
            }
            ev.consume();
        });

        Button sendBtn = (Button) dlg.getDialogPane().lookupButton(sendType);
        Runnable updateSendButton = () -> sendBtn.setDisable(
                sourceCombo.getValue() == null || formatCombo.getValue() != Format.WORDPRESS);
        sourceCombo.valueProperty().addListener((obs, old, p) -> updateSendButton.run());
        formatCombo.valueProperty().addListener((obs, old, f) -> updateSendButton.run());
        updateSendButton.run();
        sendBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            Post post = sourceCombo.getValue();
            if (post != null) sendToWordPress(trip, post, ta.getText(), sourceCombo);
            ev.consume();
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

    private void previewPostInBrowser(Trip trip, Post post, ImpressionSource images, boolean favesOnly) {
        try {
            openInBrowser(diaryExporter.exportPostAsHtml(trip, post, images, favesOnly));
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
     * Implements the send rules (see {@code docs/60_wordpress_api_draft.md}): no {@code wp_id} →
     * create a draft; otherwise GET the existing post first — 404 offers creating a new draft,
     * a newer {@code modified_gmt} warns before overwriting or offers a new draft, a published
     * post asks for confirmation before updating (status itself is never changed from here).
     * Runs the network calls on the FX thread (acceptable for a one-off, user-initiated action);
     * confirmation dialogs are plain {@code showAndWait()}.
     */
    private void sendToWordPress(Trip trip, Post post, String markup, ComboBox<Post> sourceCombo) {
        AppSettings.WordPress wp = settingsStore.load().getWordPress();
        if (wp.getAccessToken().isBlank()) {
            statusSink.error("Connect to WordPress in ⚙ Edit Settings first");
            return;
        }
        WordPressApiClient api = new WordPressApiClient();
        try {
            WordPressApiClient.PostResult result;
            if (post.wpId() == null || post.wpId().isBlank()) {
                result = api.createDraft(wp, post.title(), markup);
            } else {
                result = sendUpdate(api, wp, post, markup);
                if (result == null) return; // user cancelled a conflict prompt
            }
            Post updated = post.withWordPress(result.id(), result.modified());
            store.savePost(trip.ref(), updated);
            addPending.accept(trip.ref().path() + "/posts/" + post.slug(), "Update");
            // Keep the combo's selection pointing at the now-updated Post (same slug, new wp fields)
            int index = sourceCombo.getItems().indexOf(post);
            if (index >= 0) sourceCombo.getItems().set(index, updated);
            statusSink.status("Sent to WordPress: " + result.link());
        } catch (WordPressException e) {
            statusSink.error("Send to WordPress failed: " + e.getMessage());
        }
    }

    /** @return the update result, or null if the user cancelled a conflict/published-post prompt. */
    private WordPressApiClient.PostResult sendUpdate(WordPressApiClient api, AppSettings.WordPress wp, Post post,
                                                     String markup) {
        WordPressApiClient.PostResult existing;
        try {
            existing = api.getPost(wp, post.wpId());
        } catch (WordPressApiClient.NotFoundException e) {
            if (!confirm("WordPress post not found", "The post " + post.wpId()
                    + " was not found on WordPress (deleted or trashed?). Create a new draft instead?")) {
                return null;
            }
            return api.createDraft(wp, post.title(), markup);
        }
        if (post.wpModified() != null && existing.modified() != null && existing.modified().isAfter(post.wpModified())) {
            ButtonType overwrite = new ButtonType("Overwrite", ButtonBar.ButtonData.OTHER);
            ButtonType createNew = new ButtonType("Create New Draft", ButtonBar.ButtonData.OTHER);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "This post was edited on WordPress since the last send (e.g. images or wording). "
                            + "Overwrite it with TripTale's version, create a new draft instead, or cancel?",
                    overwrite, createNew, ButtonType.CANCEL);
            alert.setHeaderText("WordPress post changed since last send");
            Dialogs.applyStylesheet(alert.getDialogPane());
            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) return null;
            if (choice.get() == createNew) return api.createDraft(wp, post.title(), markup);
            // else: Overwrite — fall through to the update below
        }
        if ("publish".equals(existing.status())
                && !confirm("Published post", "This post is published on WordPress — overwriting goes live immediately. Continue?")) {
            return null;
        }
        return api.updatePost(wp, post.wpId(), post.title(), markup);
    }

    private boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(header);
        Dialogs.applyStylesheet(alert.getDialogPane());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    /**
     * WordPress can only hotlink backpack images, and only with a public base URL in Settings;
     * Markdown offers every source. Keeps the current choice where it is still offered.
     */
    private void offerImages(Format format, ComboBox<Images> imagesCombo) {
        Images current = imagesCombo.getValue();
        if (format == Format.WORDPRESS) {
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
