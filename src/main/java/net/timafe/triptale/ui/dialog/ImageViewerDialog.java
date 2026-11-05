package net.timafe.triptale.ui.dialog;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.impressions.ImageFilter;
import net.timafe.triptale.impressions.GalleryLayout;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.ExifInfo;
import net.timafe.triptale.storage.ExifReader;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Clipboards;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.RecentFolder;
import net.timafe.triptale.ui.StatusSink;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.util.Coordinates;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Paging photo viewer for a day's impressions: one image at a time with EXIF metadata,
 * keyboard and button navigation, and an "Open in Maps" split button when the photo is geotagged —
 * its menu sets the photo's location as the tale's start or end point (through {@link RoutePoints},
 * so the main window applies it as an unsaved form change).
 * Below the image, the user picks the source (trip attachments, local photo library, or an
 * ad-hoc folder), can narrow it to faves, put the shown image into the day's backpack or unpack
 * it again (todo 47), and preview how the day's gallery would be laid out in the exports (todo 41).
 * <p>
 * The layout is deliberately pinned to fixed pixel sizes (640x480 image box, fixed-width meta
 * labels, fixed-height info header). With {@code preserveRatio=true} an {@link ImageView}'s own
 * layout bounds shrink to the scaled image, and EXIF strings vary in length — letting either
 * drive the layout makes every navigation step resize the dialog, which reads as a flicker.
 */
public final class ImageViewerDialog {

    private static final int IMAGE_W = 640;
    private static final int IMAGE_H = 480;
    /** A new point closer than this to the current one replaces it without asking. */
    private static final double SAME_SPOT_METERS = 10;
    private static final ButtonType MAP_BUTTON = new ButtonType("Open in Maps", ButtonBar.ButtonData.RIGHT);

    /** The two points of a tale's route. */
    public enum RoutePoint {
        START("📍", "Start"), END("🏁", "End");

        final String emoji;
        final String label;
        RoutePoint(String emoji, String label) { this.emoji = emoji; this.label = label; }
    }

    /**
     * The tale's route points as the main window currently holds them (null = unset), and how to
     * apply a new one there. The viewer never persists anything itself.
     */
    public interface RoutePoints {
        Coordinates.LatLon get(RoutePoint point);

        /** Applies the point and saves the day if it can; true when the day was saved. */
        boolean set(RoutePoint point, Coordinates.LatLon value);
    }

    /** The source combo's entries; {@link #FOLDER} gets its directory from the chooser. */
    private enum SourceChoice {
        ATTACHMENTS("🎒 " + ImpressionSource.TRIP_ATTACHMENTS.label()),
        PHOTO_LIB("🗂 " + ImpressionSource.PHOTO_LIBRARY.label()),
        FOLDER("📁 Pick Folder");

        final String label;
        SourceChoice(String label) { this.label = label; }
    }

    private final ImpressionsService impressions;
    private final ExifReader exifReader;
    private final BrowserLauncher browser;
    private final StatusSink statusSink;
    private final RecentFolder recentFolder;

    public ImageViewerDialog(ImpressionsService impressions, ExifReader exifReader, BrowserLauncher browser,
                             StatusSink statusSink, RecentFolder recentFolder) {
        this.impressions = impressions;
        this.exifReader = exifReader;
        this.browser = browser;
        this.statusSink = statusSink;
        this.recentFolder = recentFolder;
    }

    /**
     * Shows the viewer modally for the trip's day. {@code onImported} runs on the FX thread after
     * each successful import, so the caller can refresh what depends on the attachments;
     * {@code route} receives a photo's location set as start or end point.
     */
    public void show(Trip trip, LocalDate date, Runnable onImported, RoutePoints route) {
        new Session(trip, date, onImported, route).show();
    }

    /** State and controls of one open viewer. */
    private final class Session {
        private final Trip trip;
        private final LocalDate date;
        private final Runnable onImported;
        private final RoutePoints route;
        private final ImageFilter faveFilter = impressions.faveFilter();
        /** Images of the source already in the day's backpack (same name and size). */
        private Set<Path> packed = Set.of();

        /** Null while Pick Folder has no folder yet. */
        private ImpressionSource source;
        private Path pickedFolder;
        private List<Path> all = List.of();
        private List<Path> visible = List.of();
        private int index;

        private final Dialog<Void> dlg = new Dialog<>();
        private final ImageView imageView = new ImageView();
        private final Label placeholder = new Label();
        private final Label counter = new Label();
        private final Label pathLabel = new Label();
        private final Button copyDirBtn = new Button("📋");
        private final Label metaLabel = new Label();
        private final Button firstBtn = new Button("⏮");
        private final Button prevBtn = new Button("◀");
        private final Button nextBtn = new Button("▶");
        private final Button lastBtn = new Button("⏭");
        private final Button putBtn = new Button("＋ Put into");
        private final Button unpackBtn = new Button("－ Unpack from");
        private final Pane galleryPane = new Pane();
        private final Button galleryBtn = new Button("▦ Gallery Layout");
        private boolean galleryShown;
        private final ComboBox<SourceChoice> sourceCombo = new ComboBox<>();
        private final Label dirLabel = new Label();
        private final Button copySourceDirBtn = new Button("📋");
        private final Button pickBtn = new Button("📂");
        private final CheckBox favesCheck = new CheckBox();
        private final Label packedSummary = new Label();
        private final MenuItem setStartItem = new MenuItem();
        private final MenuItem setEndItem = new MenuItem();
        private final SplitMenuButton mapButton = new SplitMenuButton(setStartItem, setEndItem);
        private final Tooltip mapTooltip = new Tooltip();

        Session(Trip trip, LocalDate date, Runnable onImported, RoutePoints route) {
            this.trip = trip;
            this.date = date;
            this.onImported = onImported;
            this.route = route;
            // A dialog pane only knows plain buttons for its button types; swap in the split
            // button for Open in Maps. Not being a pane-created Button, it also doesn't close the dialog.
            dlg.setDialogPane(new DialogPane() {
                @Override
                protected Node createButton(ButtonType type) {
                    if (type != MAP_BUTTON) return super.createButton(type);
                    mapButton.setText(type.getText());
                    ButtonBar.setButtonData(mapButton, type.getButtonData());
                    return mapButton;
                }
            });
        }

        void show() {
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(IMAGE_W);
            imageView.setFitHeight(IMAGE_H);
            placeholder.getStyleClass().add("impressions-placeholder");
            // Wrapping the ImageView in a fixed-size, centered box keeps the layout footprint
            // constant at 640x480 regardless of the photo's aspect ratio, so nothing above or
            // below it ever moves between images.
            galleryPane.setMinSize(IMAGE_W, IMAGE_H);
            galleryPane.setPrefSize(IMAGE_W, IMAGE_H);
            galleryPane.setMaxSize(IMAGE_W, IMAGE_H);
            galleryPane.setVisible(false);
            StackPane imageBox = new StackPane(imageView, placeholder, galleryPane);
            imageBox.setAlignment(Pos.CENTER);
            imageBox.setMinSize(IMAGE_W, IMAGE_H);
            imageBox.setPrefSize(IMAGE_W, IMAGE_H);
            imageBox.setMaxSize(IMAGE_W, IMAGE_H);

            pathLabel.setStyle("-fx-font-weight: bold;");
            pathLabel.setWrapText(false);
            // Center ellipsis keeps a bit of the directory (start) and the full filename tail
            // (end) visible, cutting only the middle — and capping the width well below the
            // 640px image width keeps the line short instead of stretching to fill it.
            pathLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
            pathLabel.setAlignment(Pos.CENTER);
            pathLabel.setMaxWidth(420);

            copyDirBtn.setTooltip(new Tooltip("Copy directory to clipboard"));
            copyDirBtn.setOnAction(ev -> {
                if (visible.isEmpty()) return;
                Clipboards.putString(visible.get(index).getParent().toString());
                statusSink.status("Directory copied to clipboard");
            });

            HBox pathRow = fixedWidth(new HBox(6, pathLabel, copyDirBtn));
            pathRow.setAlignment(Pos.CENTER);

            // Fixed width matching the image, with ellipsis instead of wrapping/growing — otherwise
            // a longer/shorter EXIF string changes the label's preferred width on every navigation,
            // which grows/shrinks the whole dialog.
            fixedWidth(metaLabel);
            metaLabel.setWrapText(false);
            metaLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            metaLabel.setAlignment(Pos.CENTER);

            VBox topInfo = new VBox(2, pathRow, metaLabel);
            topInfo.setAlignment(Pos.CENTER);
            // Fixed 2-line height so the image never shifts vertically once EXIF data is
            // populated (or when it's missing/short).
            topInfo.setMinHeight(36);
            topInfo.setPrefHeight(36);
            topInfo.setMaxHeight(36);

            firstBtn.setOnAction(ev -> go(0));
            prevBtn.setOnAction(ev -> go(index - 1));
            nextBtn.setOnAction(ev -> go(index + 1));
            lastBtn.setOnAction(ev -> go(visible.size() - 1));
            // Fixed width (room for "99 / 99"), so the buttons don't shift when the digits change.
            counter.setMinWidth(56);
            counter.setPrefWidth(56);
            counter.setMaxWidth(56);
            counter.setAlignment(Pos.CENTER);
            putBtn.setOnAction(ev -> putIntoBackpack());
            unpackBtn.setOnAction(ev -> unpackFromBackpack());
            // Two buttons at fixed places instead of one toggle: a click at the same spot never
            // adds or deletes depending on state. Unpack is muted and set apart from the nav.
            putBtn.setGraphic(backpackChip());
            putBtn.setContentDisplay(ContentDisplay.RIGHT);
            unpackBtn.setGraphic(backpackChip());
            unpackBtn.setContentDisplay(ContentDisplay.RIGHT);
            putBtn.setMinWidth(Region.USE_PREF_SIZE);
            unpackBtn.setMinWidth(Region.USE_PREF_SIZE);
            unpackBtn.getStyleClass().add("unpack-button");
            Region navGap = new Region();
            navGap.setMinWidth(16);
            HBox nav = fixedWidth(new HBox(8, firstBtn, prevBtn, counter, nextBtn, lastBtn, navGap, putBtn, unpackBtn));
            nav.setAlignment(Pos.CENTER_LEFT);
            nav.setPadding(new Insets(0, 0, 4, 0));

            sourceCombo.getItems().setAll(SourceChoice.values());
            sourceCombo.setConverter(new StringConverter<>() {
                @Override public String toString(SourceChoice c) { return c == null ? "" : c.label; }
                @Override public SourceChoice fromString(String s) { return null; }
            });
            dirLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
            // The path is the least important part of the row: it shrinks first, the label and combo never do.
            dirLabel.setMinWidth(0);
            dirLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(dirLabel, Priority.ALWAYS);
            pickBtn.setTooltip(new Tooltip("Pick a different folder"));
            pickBtn.setOnAction(ev -> pickFolder());
            copySourceDirBtn.setTooltip(new Tooltip("Copy folder path to clipboard"));
            copySourceDirBtn.setOnAction(ev -> sourceDirectory().ifPresent(dir -> {
                Clipboards.putString(dir.toString());
                statusSink.status("Folder path copied to clipboard");
            }));
            Label sourceLabel = new Label("Source:");
            sourceLabel.setMinWidth(Region.USE_PREF_SIZE);
            sourceCombo.setMinWidth(Region.USE_PREF_SIZE);
            copySourceDirBtn.setMinWidth(Region.USE_PREF_SIZE);
            pickBtn.setMinWidth(Region.USE_PREF_SIZE);
            HBox sourceRow = fixedWidth(new HBox(8, sourceLabel, sourceCombo, dirLabel, copySourceDirBtn, pickBtn));
            sourceRow.setAlignment(Pos.CENTER_LEFT);

            favesCheck.setOnAction(ev -> applyFaves());
            galleryBtn.setOnAction(ev -> toggleGallery());
            galleryBtn.setTooltip(new Tooltip("Preview how this day's backpack images are laid out in the gallery of the exports"));
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox actionRow = fixedWidth(new HBox(8, favesCheck, packedSummary, spacer, galleryBtn));
            actionRow.setAlignment(Pos.CENTER_LEFT);

            VBox content = fixedWidth(new VBox(6, topInfo, imageBox, nav, new Separator(), sourceRow, actionRow));
            content.setAlignment(Pos.CENTER_LEFT);

            dlg.setResizable(true);
            dlg.getDialogPane().getStyleClass().add("image-viewer-dialog");
            dlg.getDialogPane().getButtonTypes().setAll(MAP_BUTTON, ButtonType.CLOSE);
            mapButton.setTooltip(mapTooltip);
            mapButton.setOnAction(ev -> currentExif()
                    .filter(ExifInfo::hasLocation)
                    .ifPresent(exif -> browser.open(exif.mapsUrl())));
            setStartItem.setOnAction(ev -> setRoutePoint(RoutePoint.START));
            setEndItem.setOnAction(ev -> setRoutePoint(RoutePoint.END));
            // Lock the dialog's initial width so it can't grow/shrink between images — the content
            // is fixed at 640, the dialog pane just adds its own padding. Still user-resizable
            // afterwards since we only set the preferred width.
            dlg.getDialogPane().setPrefWidth(680);

            // Date is shown in the window title only — it's redundant above the image and was
            // taking up a whole header row of vertical space.
            dlg.setTitle("📷 Impression Curator · " + UiText.friendlyDate(date));
            dlg.getDialogPane().setContent(content);
            dlg.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == KeyCode.LEFT) {
                    go(index - 1);
                    ev.consume();
                } else if (ev.getCode() == KeyCode.RIGHT) {
                    go(index + 1);
                    ev.consume();
                } else if (ev.getCode() == KeyCode.SPACE && !putBtn.isDisabled()) {
                    putIntoBackpack();
                    ev.consume();
                } else if (ev.getCode() == KeyCode.BACK_SPACE && ev.isShortcutDown() && !unpackBtn.isDisabled()) {
                    unpackFromBackpack();
                    ev.consume();
                }
            });
            Dialogs.applyStylesheet(dlg.getDialogPane());

            SourceChoice initial = defaultChoice();
            sourceCombo.setValue(initial);
            selectSource(initial);
            sourceCombo.valueProperty().addListener((obs, old, choice) -> {
                if (choice != null) selectSource(choice);
            });
            dlg.showAndWait();
        }

        /**
         * The photo library while it still has images that aren't in the backpack yet (it often
         * grows), else the backpack if it has images, else the library if it has any, else Pick
         * Folder. Counts follow the base filter, so GPX, PDF etc. in the backpack don't count.
         */
        private SourceChoice defaultChoice() {
            List<Path> library = impressions.images(ImpressionSource.PHOTO_LIBRARY, trip, date);
            if (impressions.alreadyImported(trip, date, library).size() < library.size()) {
                return SourceChoice.PHOTO_LIB;
            }
            if (!impressions.images(ImpressionSource.TRIP_ATTACHMENTS, trip, date).isEmpty()) {
                return SourceChoice.ATTACHMENTS;
            }
            return library.isEmpty() ? SourceChoice.FOLDER : SourceChoice.PHOTO_LIB;
        }

        private void selectSource(SourceChoice choice) {
            pickBtn.setDisable(choice != SourceChoice.FOLDER);
            switch (choice) {
                case ATTACHMENTS -> loadSource(ImpressionSource.TRIP_ATTACHMENTS);
                case PHOTO_LIB -> loadSource(ImpressionSource.PHOTO_LIBRARY);
                case FOLDER -> {
                    loadSource(pickedFolder == null ? null : new ImpressionSource.Folder(pickedFolder));
                    // Opening the dialog on Pick Folder doesn't pop the chooser (that was irritating);
                    // switching to it does. Defer, so the combo's popup is closed first.
                    if (pickedFolder == null && dlg.isShowing()) Platform.runLater(this::pickFolder);
                }
            }
        }

        private void pickFolder() {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Pick a folder with impressions");
            chooser.setInitialDirectory(recentFolder.initial());
            File dir = chooser.showDialog(dlg.getDialogPane().getScene().getWindow());
            if (dir == null) return;
            recentFolder.remember(dir);
            pickedFolder = dir.toPath();
            loadSource(new ImpressionSource.Folder(pickedFolder));
        }

        /** Switches to {@code newSource} (null = nothing to show yet). */
        private void loadSource(ImpressionSource newSource) {
            source = newSource;
            all = source == null ? List.of() : impressions.images(source, trip, date);
            updatePacked();
            index = 0;
            dirLabel.setText(describeDirectory());
            dirLabel.setTooltip(new Tooltip(dirLabel.getText()));
            copySourceDirBtn.setDisable(sourceDirectory().isEmpty());
            updateFavesCheck();
            applyFaves();
        }

        private Optional<Path> sourceDirectory() {
            return source == null ? Optional.empty() : impressions.directory(source, trip, date);
        }

        private String describeDirectory() {
            if (source == null) return "No folder picked";
            return sourceDirectory()
                    .map(UiText::homeRelative)
                    .orElse(switch (source) {
                        case ImpressionSource.PhotoLibrary p -> impressions.photoLibraryConfigured()
                                ? "Photo library folder not found" : "No impressions file pattern configured";
                        case ImpressionSource.TripAttachments a -> "Nothing in the backpack for this day yet";
                        case ImpressionSource.Folder f -> "Folder not found";
                    });
        }

        /** Trivially "all packed" for the backpack itself — every image shown is already in it. */
        private void updatePacked() {
            boolean fromAttachments = source instanceof ImpressionSource.TripAttachments;
            packed = source == null ? Set.of() : fromAttachments ? Set.copyOf(all) : impressions.alreadyImported(trip, date, all);
            packedSummary.setText(packed.isEmpty() ? "" : packed.size() + " packed");
            packedSummary.setGraphic(packed.isEmpty() ? null : backpackChip());
            packedSummary.setContentDisplay(ContentDisplay.LEFT);
            packedSummary.setTooltip(packed.isEmpty() ? null : new Tooltip(fromAttachments
                    ? "This is the backpack — everything here is already imported"
                    : "Already in this day's backpack (same name and size)"));
        }

        private void updateFavesCheck() {
            int faves = faveFilter.apply(all).size();
            boolean usable = !faveFilter.isEmpty() && faves > 0;
            if (faveFilter.isEmpty()) {
                favesCheck.setText("Faves only (no pattern defined)");
            } else if (faves == 0) {
                favesCheck.setText("Faves only (no matching faves)");
            } else {
                favesCheck.setText("Faves only (" + faves + "/" + all.size() + ")");
            }
            favesCheck.setTooltip(new Tooltip(faveFilter.isEmpty()
                    ? "Set an impressions fave filter in the settings, e.g. *+.*"
                    : "Fave filter: " + faveFilter));
            favesCheck.setDisable(!usable);
            if (!usable) favesCheck.setSelected(false);
        }

        /** Recomputes the visible set from the faves checkbox. */
        private void applyFaves() {
            Path current = visible.isEmpty() ? null : visible.get(index);
            visible = favesCheck.isSelected() ? faveFilter.apply(all) : all;
            int keep = current == null ? -1 : visible.indexOf(current);
            index = keep >= 0 ? keep : 0;
            refresh();
        }

        private void go(int newIndex) {
            if (visible.isEmpty()) return;
            index = Math.max(0, Math.min(visible.size() - 1, newIndex));
            refresh();
        }

        private Optional<ExifInfo> currentExif() {
            return visible.isEmpty() ? Optional.empty() : Optional.of(exifReader.read(visible.get(index)));
        }

        /**
         * Hands the current photo's location to the main window as start or end point. Asks first
         * when that replaces a different point, or when the photo was taken on another day.
         */
        private void setRoutePoint(RoutePoint point) {
            Optional<ExifInfo> exif = currentExif().filter(ExifInfo::hasLocation);
            if (exif.isEmpty()) return;
            Path photo = visible.get(index);
            Coordinates.LatLon value = Coordinates.rounded(exif.get().latitude(), exif.get().longitude());

            List<String> warnings = new ArrayList<>();
            Coordinates.LatLon current = route.get(point);
            if (current != null && Coordinates.distanceMeters(current, value) > SAME_SPOT_METERS) {
                warnings.add("This replaces the tale's " + point.label.toLowerCase(Locale.ROOT) + " point.\n"
                        + "Current: " + describePoint(current) + "\n"
                        + "New:     " + describePoint(value));
            }
            LocalDateTime taken = exif.get().captureTime();
            if (taken != null && !taken.toLocalDate().equals(date)) {
                warnings.add("The photo was taken on " + UiText.friendlyDate(taken.toLocalDate())
                        + ", not on this tale's day.");
            }
            if (!warnings.isEmpty() && !confirmRoutePoint(point, warnings)) return;

            boolean saved = route.set(point, value);
            refresh();
            statusSink.status(point.emoji + " " + point.label + " set from " + photo.getFileName()
                    + (saved ? " — day saved" : " (unsaved — add a title and tale to save the day)"));
        }

        private boolean confirmRoutePoint(RoutePoint point, List<String> warnings) {
            ButtonType apply = new ButtonType("Set as " + point.label, ButtonBar.ButtonData.OK_DONE);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, String.join("\n\n", warnings),
                    apply, ButtonType.CANCEL);
            confirm.initOwner(dlg.getDialogPane().getScene().getWindow());
            confirm.setTitle("📷 Impression Curator");
            confirm.setHeaderText("Set this photo's location as " + point.label.toLowerCase(Locale.ROOT) + " point?");
            Dialogs.applyStylesheet(confirm.getDialogPane());
            return confirm.showAndWait().filter(apply::equals).isPresent();
        }

        /** Copies the shown image into the day's backpack. */
        private void putIntoBackpack() {
            if (visible.isEmpty()) return;
            Path image = visible.get(index);
            List<Path> copies;
            try {
                copies = impressions.importToAttachments(trip, date, List.of(image));
            } catch (RuntimeException e) {
                statusSink.error("Put into backpack failed: " + UiText.describe(e));
                return;
            }
            updatePacked();
            refresh();
            statusSink.status("🎒 Put " + image.getFileName() + " into " + UiText.homeRelative(copies.getFirst().getParent()));
            onImported.run();
        }

        /**
         * Removes the shown image's backpack copy. Silent when a copy outside the backpack exists
         * (any other source shows its own file, or the library/picked folder has the same name and
         * size); otherwise asks first, with Cancel as the default.
         */
        private void unpackFromBackpack() {
            if (visible.isEmpty()) return;
            Path shown = visible.get(index);
            boolean fromAttachments = source instanceof ImpressionSource.TripAttachments;
            Optional<Path> copy = fromAttachments ? Optional.of(shown) : impressions.backpackCopy(trip, date, shown);
            if (copy.isEmpty()) return;

            boolean hasMaster = !fromAttachments || impressions.hasMasterCopy(copy.get(), masterCandidates());
            if (!hasMaster && !confirmUnpack(copy.get())) return;
            try {
                impressions.unpack(trip, date, copy.get());
            } catch (RuntimeException e) {
                statusSink.error("Unpack failed: " + UiText.describe(e));
                return;
            }
            int keep = index;
            if (fromAttachments) {
                loadSource(source);
                go(keep);
            } else {
                updatePacked();
                refresh();
            }
            statusSink.status("Unpacked " + shown.getFileName() + " from 🎒"
                    + (hasMaster ? " (copy still outside the backpack)" : ""));
            onImported.run();
        }

        /** Images outside the backpack that could be the master of a backpack file. */
        private List<Path> masterCandidates() {
            List<Path> candidates = new ArrayList<>(impressions.images(ImpressionSource.PHOTO_LIBRARY, trip, date));
            if (pickedFolder != null) {
                candidates.addAll(impressions.images(new ImpressionSource.Folder(pickedFolder), trip, date));
            }
            return candidates;
        }

        private boolean confirmUnpack(Path file) {
            ButtonType remove = new ButtonType("Unpack anyway", ButtonBar.ButtonData.OTHER);
            Alert confirm = new Alert(Alert.AlertType.WARNING,
                    "No copy of " + file.getFileName() + " was found in the photo library"
                            + (pickedFolder == null ? "" : " or the picked folder") + ".\n\n"
                            + "Unpacking deletes the only copy here"
                            + ", and the bucket copy goes at the next backpack sync.",
                    remove, ButtonType.CANCEL);
            confirm.initOwner(dlg.getDialogPane().getScene().getWindow());
            confirm.setTitle("📷 Impression Curator");
            confirm.setHeaderText("Unpack the only copy of this image?");
            Dialogs.applyStylesheet(confirm.getDialogPane());
            // Enter must not confirm the delete: Cancel is the default button
            ((Button) confirm.getDialogPane().lookupButton(remove)).setDefaultButton(false);
            ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
            return confirm.showAndWait().filter(remove::equals).isPresent();
        }

        private void toggleGallery() {
            galleryShown = !galleryShown;
            refresh();
        }

        /** The day's backpack images as the exports would list them (faves only if ticked). */
        private List<Path> galleryImages() {
            List<Path> images = impressions.images(ImpressionSource.TRIP_ATTACHMENTS, trip, date);
            return favesCheck.isSelected() ? faveFilter.apply(images) : images;
        }

        /** Draws the schematic: cell shapes follow the images' displayed aspect ratios. */
        private void drawGallery() {
            galleryPane.getChildren().clear();
            List<Path> images = galleryImages();
            if (images.isEmpty()) {
                Text none = new Text(20, 30, "No images in the backpack for this day");
                none.setFill(Color.GRAY);
                galleryPane.getChildren().add(none);
                return;
            }
            List<Double> aspects = images.stream().map(p -> exifReader.read(p).displayAspect()).toList();
            List<GalleryLayout.Row> rows = GalleryLayout.plan(aspects, impressions.gridColumns());
            double gap = 4;
            double total = rows.stream().mapToDouble(r -> r.heightRatio() * IMAGE_W).sum() + gap * (rows.size() - 1);
            double scale = Math.min(1, IMAGE_H / total);
            double width = IMAGE_W * scale;
            double x0 = (IMAGE_W - width) / 2;
            double y = 0;
            Path current = visible.isEmpty() ? null : visible.get(index);
            for (GalleryLayout.Row row : rows) {
                double h = row.heightRatio() * width;
                int n = row.cells().size();
                double cellW = (width - gap * (n - 1)) / n;
                for (int c = 0; c < n; c++) {
                    Path image = images.get(row.cells().get(c).index());
                    boolean isCurrent = current != null && image.getFileName().equals(current.getFileName());
                    Rectangle rect = new Rectangle(x0 + c * (cellW + gap), y, cellW, h);
                    rect.setFill(Color.web(isCurrent ? "#2a9d8f" : "#3b4a4f"));
                    rect.setStroke(isCurrent ? Color.web("#e9c46a") : Color.web("#566a70"));
                    Tooltip.install(rect, new Tooltip(image.getFileName().toString()));
                    Text label = new Text(rect.getX() + 6, rect.getY() + 16, Integer.toString(row.cells().get(c).index() + 1));
                    label.setFill(Color.web("#d0dcdf"));
                    label.setMouseTransparent(true);
                    rect.setOnMouseClicked(ev -> {
                        int at = visible.indexOf(image);
                        if (at >= 0) {
                            galleryShown = false;
                            go(at);
                        }
                    });
                    galleryPane.getChildren().addAll(rect, label);
                }
                y += h + gap;
            }
        }

        private void refresh() {
            boolean empty = visible.isEmpty();
            placeholder.setVisible(empty && !galleryShown);
            placeholder.setText(placeholderText());
            imageView.setVisible(!empty && !galleryShown);
            galleryPane.setVisible(galleryShown);
            galleryBtn.setText(galleryShown ? "🖼 Image Viewer" : "▦ Gallery Layout");
            if (galleryShown) drawGallery();
            counter.setText(empty ? "0 / 0" : (index + 1) + " / " + visible.size());
            firstBtn.setDisable(empty || index == 0);
            prevBtn.setDisable(empty || index == 0);
            nextBtn.setDisable(empty || index == visible.size() - 1);
            lastBtn.setDisable(empty || index == visible.size() - 1);
            copyDirBtn.setDisable(empty);

            if (empty) {
                imageView.setImage(null);
                pathLabel.setText("");
                metaLabel.setText("");
                mapButton.setDisable(true);
                mapTooltip.setText("No image");
            } else {
                Path p = visible.get(index);
                imageView.setImage(new Image(p.toUri().toString(), IMAGE_W, IMAGE_H, true, true, true));
                pathLabel.setText(UiText.homeRelative(p));
                ExifInfo exif = exifReader.read(p);
                metaLabel.setText(describeExif(exif, sizeOf(p)));
                mapButton.setDisable(!exif.hasLocation());
                mapTooltip.setText(exif.hasLocation()
                        ? "Open coordinates in Google Maps — ▾ to use them as the tale's start or end point"
                        : "No geo data");
            }
            boolean hasStart = route.get(RoutePoint.START) != null;
            setStartItem.setText(RoutePoint.START.emoji + " Set as Start");
            setEndItem.setText(RoutePoint.END.emoji + (hasStart ? " Set as End" : " Set as End (set a start point first)"));
            setEndItem.setDisable(!hasStart);

            boolean isPacked = !empty && packed.contains(visible.get(index));
            putBtn.setDisable(empty || isPacked);
            putBtn.setTooltip(new Tooltip(isPacked
                    ? "Already in this day's backpack (same name and size)"
                    : "Copy this image into this day's backpack (Space)"));
            unpackBtn.setDisable(!isPacked);
            unpackBtn.setTooltip(new Tooltip(isPacked
                    ? "Remove this image from the backpack — asks first if no other copy exists (Cmd/Ctrl+Backspace)"
                    : "Not in this day's backpack"));
        }

        private String placeholderText() {
            if (source == null) return "No folder picked — use 📂 to choose one";
            String where = source instanceof ImpressionSource.Folder ? "this folder" : source.label();
            return favesCheck.isSelected() ? "No faves in " + where : "No images in " + where;
        }
    }

    /** The backpack emoji on a light chip: the red glyph has little contrast on the dark buttons. */
    private static Label backpackChip() {
        Label chip = new Label("🎒");
        chip.getStyleClass().add("backpack-chip");
        return chip;
    }

    private static <T extends Region> T fixedWidth(T region) {
        region.setMinWidth(IMAGE_W);
        region.setPrefWidth(IMAGE_W);
        region.setMaxWidth(IMAGE_W);
        return region;
    }

    /** The file size in bytes, or null if it can't be read. */
    private static Long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return null;
        }
    }

    /** One-line summary: camera · aperture · ISO · exposure · dimensions · file size · coordinates. */
    private static String describeExif(ExifInfo exif, Long fileSize) {
        StringBuilder sb = new StringBuilder();
        if (exif.hasCameraData()) {
            appendPart(sb, exif.cameraModel());
            appendPart(sb, exif.aperture());
            appendPart(sb, exif.iso());
            appendPart(sb, exif.exposureTime());
        } else {
            sb.append("No camera data");
        }
        if (exif.dimensions() != null) {
            sb.append(" · ").append(exif.dimensions());
        }
        if (fileSize != null) {
            sb.append(" · ").append(UiText.fileSize(fileSize));
        }
        sb.append(exif.hasLocation()
                ? " · 📍 " + Coordinates.toDdm(exif.latitude(), exif.longitude())
                : " · 📍 Uncharted");
        return sb.toString();
    }

    /** DDM for reading plus the 5-decimal value, since whole minutes can't tell nearby points apart. */
    private static String describePoint(Coordinates.LatLon p) {
        return String.format(Locale.ROOT, "%s  (%.5f, %.5f)", Coordinates.toDdm(p.lat(), p.lon()), p.lat(), p.lon());
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null) return;
        if (sb.length() > 0) sb.append(" · ");
        sb.append(part);
    }
}
