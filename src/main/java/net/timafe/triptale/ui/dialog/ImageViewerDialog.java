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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.impressions.ImageFilter;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Paging photo viewer for a day's impressions: one image at a time with EXIF metadata,
 * keyboard and button navigation, and an "Open in Maps" split button when the photo is geotagged —
 * its menu sets the photo's location as the tale's start or end point (through {@link RoutePoints},
 * so the main window applies it as an unsaved form change).
 * Below the image, the user picks the source (trip attachments, local photo library, or an
 * ad-hoc folder), can narrow it to faves, select images and import them as attachments of the
 * day (todo 47).
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
        private final Map<Path, ExifInfo> exifCache = new HashMap<>();
        private final Set<Path> selected = new LinkedHashSet<>();
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
        private final CheckBox selectCheck = new CheckBox("Select");
        private final Label packedBadge = new Label("🎒 In backpack");
        private final ComboBox<SourceChoice> sourceCombo = new ComboBox<>();
        private final Label dirLabel = new Label();
        private final Button copySourceDirBtn = new Button("📋");
        private final Button pickBtn = new Button("📂");
        private final CheckBox favesCheck = new CheckBox();
        private final Label packedSummary = new Label();
        private final Button selectAllBtn = new Button();
        private final Button importBtn = new Button();
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
            StackPane imageBox = new StackPane(imageView, placeholder);
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
            selectCheck.setOnAction(ev -> toggleSelected());

            // Stays managed when hidden, so the nav row doesn't shift between images.
            // Tooltip text is set per-source in updatePacked().
            packedBadge.setVisible(false);
            HBox nav = new HBox(8, firstBtn, prevBtn, counter, nextBtn, lastBtn, selectCheck, packedBadge);
            nav.setAlignment(Pos.CENTER);
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
            selectAllBtn.setOnAction(ev -> toggleSelectAll());
            importBtn.setOnAction(ev -> importSelected());
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox actionRow = fixedWidth(new HBox(8, favesCheck, packedSummary, spacer, selectAllBtn, importBtn));
            actionRow.setAlignment(Pos.CENTER_LEFT);

            VBox content = fixedWidth(new VBox(6, topInfo, imageBox, nav, new Separator(), sourceRow, actionRow));
            content.setAlignment(Pos.CENTER);

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
                } else if (ev.getCode() == KeyCode.SPACE && !visible.isEmpty()
                        && !(source instanceof ImpressionSource.TripAttachments)) {
                    toggleSelected();
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

        /** Attachments if the day has any, else the photo library if it has any, else Pick Folder. */
        private SourceChoice defaultChoice() {
            if (!impressions.images(ImpressionSource.TRIP_ATTACHMENTS, trip, date).isEmpty()) {
                return SourceChoice.ATTACHMENTS;
            }
            if (!impressions.images(ImpressionSource.PHOTO_LIBRARY, trip, date).isEmpty()) {
                return SourceChoice.PHOTO_LIB;
            }
            return SourceChoice.FOLDER;
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

        /** Switches to {@code newSource} (null = nothing to show yet); clears the selection. */
        private void loadSource(ImpressionSource newSource) {
            source = newSource;
            all = source == null ? List.of() : impressions.images(source, trip, date);
            selected.clear();
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
            packedSummary.setText(packed.isEmpty() ? "" : "🎒 " + packed.size() + " packed");
            packedSummary.setTooltip(packed.isEmpty() ? null : new Tooltip(fromAttachments
                    ? "This is the backpack — everything here is already imported"
                    : "Already in this day's backpack (same name and size)"));
            packedBadge.setTooltip(new Tooltip(fromAttachments
                    ? "This is the backpack — everything here is already imported"
                    : "Already in this day's backpack (same name and size) — importing it again overwrites the copy"));
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

        /** Recomputes the visible set from the faves checkbox; the selection is kept. */
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

        private void toggleSelected() {
            if (visible.isEmpty() || source instanceof ImpressionSource.TripAttachments) return;
            Path p = visible.get(index);
            if (!selected.remove(p)) selected.add(p);
            refresh();
        }

        /** Select All skips images that are already in the backpack; Unselect All clears them too. */
        private void toggleSelectAll() {
            if (source instanceof ImpressionSource.TripAttachments) return;
            if (selectAllUnselects()) {
                visible.forEach(selected::remove);
            } else {
                selected.addAll(unpackedVisible());
            }
            refresh();
        }

        private List<Path> unpackedVisible() {
            return visible.stream().filter(p -> !packed.contains(p)).toList();
        }

        /** True once everything Select All would pick is selected (and something visible is). */
        private boolean selectAllUnselects() {
            return selected.containsAll(unpackedVisible()) && visible.stream().anyMatch(selected::contains);
        }

        private Optional<ExifInfo> currentExif() {
            return visible.isEmpty() ? Optional.empty() : Optional.ofNullable(exifCache.get(visible.get(index)));
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

        private void importSelected() {
            // Keep the source's order; the selection may include images hidden by Faves only.
            List<Path> files = all.stream().filter(selected::contains).toList();
            if (files.isEmpty()) return;
            List<Path> copies;
            try {
                copies = impressions.importToAttachments(trip, date, files);
            } catch (RuntimeException e) {
                statusSink.error("Import failed: " + UiText.describe(e));
                return;
            }
            selected.clear();
            updatePacked();
            refresh();
            statusSink.status("Imported " + copies.size() + " impression" + (copies.size() == 1 ? "" : "s")
                    + " to " + UiText.homeRelative(copies.getFirst().getParent()));
            onImported.run();
        }

        private void refresh() {
            boolean empty = visible.isEmpty();
            boolean fromAttachments = source instanceof ImpressionSource.TripAttachments;
            placeholder.setVisible(empty);
            placeholder.setText(placeholderText());
            imageView.setVisible(!empty);
            counter.setText(empty ? "0 / 0" : (index + 1) + " / " + visible.size());
            firstBtn.setDisable(empty || index == 0);
            prevBtn.setDisable(empty || index == 0);
            nextBtn.setDisable(empty || index == visible.size() - 1);
            lastBtn.setDisable(empty || index == visible.size() - 1);
            // Selecting is pointless here — everything in the backpack is already imported, and
            // there's nowhere else to send it from this dialog (todo: revisit once the backpack
            // supports its own actions, e.g. removing an item).
            selectCheck.setDisable(empty || fromAttachments);
            selectCheck.setTooltip(new Tooltip(fromAttachments
                    ? "Nothing to select — these are already in the backpack"
                    : "Select this image for import (Space)"));
            copyDirBtn.setDisable(empty);

            if (empty) {
                imageView.setImage(null);
                pathLabel.setText("");
                metaLabel.setText("");
                selectCheck.setSelected(false);
                packedBadge.setVisible(false);
                mapButton.setDisable(true);
                mapTooltip.setText("No image");
            } else {
                Path p = visible.get(index);
                imageView.setImage(new Image(p.toUri().toString(), IMAGE_W, IMAGE_H, true, true, true));
                pathLabel.setText(UiText.homeRelative(p));
                ExifInfo exif = exifCache.computeIfAbsent(p, exifReader::read);
                metaLabel.setText(describeExif(exif, sizeOf(p)));
                selectCheck.setSelected(selected.contains(p));
                packedBadge.setVisible(packed.contains(p));
                mapButton.setDisable(!exif.hasLocation());
                mapTooltip.setText(exif.hasLocation()
                        ? "Open coordinates in Google Maps — ▾ to use them as the tale's start or end point"
                        : "No geo data");
            }
            boolean hasStart = route.get(RoutePoint.START) != null;
            setStartItem.setText(RoutePoint.START.emoji + " Set as Start");
            setEndItem.setText(RoutePoint.END.emoji + (hasStart ? " Set as End" : " Set as End (set a start point first)"));
            setEndItem.setDisable(!hasStart);

            boolean unselects = selectAllUnselects();
            selectAllBtn.setText(unselects ? "☐ Unselect All" : "☑ Select All");
            selectAllBtn.setDisable(empty || fromAttachments || (!unselects && unpackedVisible().isEmpty()));
            importBtn.setText("🎒 Import Selected (" + selected.size() + ")");
            importBtn.setDisable(selected.isEmpty() || fromAttachments);
            importBtn.setTooltip(new Tooltip(fromAttachments
                    ? "These images are already in this day's backpack"
                    : "Copy the selected images to this day's backpack"));
        }

        private String placeholderText() {
            if (source == null) return "No folder picked — use 📂 to choose one";
            String where = source instanceof ImpressionSource.Folder ? "this folder" : source.label();
            return favesCheck.isSelected() ? "No faves in " + where : "No images in " + where;
        }
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
