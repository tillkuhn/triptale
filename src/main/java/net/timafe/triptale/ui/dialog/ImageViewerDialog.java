package net.timafe.triptale.ui.dialog;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.timafe.triptale.storage.ExifInfo;
import net.timafe.triptale.storage.ExifReader;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Clipboards;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.StatusSink;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.util.Coordinates;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paging photo viewer for a day's impressions/faves: one image at a time with EXIF metadata,
 * keyboard and button navigation, and an "Open in Maps" action when the photo is geotagged.
 * <p>
 * The layout is deliberately pinned to fixed pixel sizes (640x480 image box, fixed-width meta
 * labels, fixed-height info header). With {@code preserveRatio=true} an {@link ImageView}'s own
 * layout bounds shrink to the scaled image, and EXIF strings vary in length — letting either
 * drive the layout makes every navigation step resize the dialog, which reads as a flicker.
 */
public final class ImageViewerDialog {

    private static final int IMAGE_W = 640;
    private static final int IMAGE_H = 480;

    private final ExifReader exifReader;
    private final BrowserLauncher browser;
    private final StatusSink statusSink;

    public ImageViewerDialog(ExifReader exifReader, BrowserLauncher browser, StatusSink statusSink) {
        this.exifReader = exifReader;
        this.browser = browser;
        this.statusSink = statusSink;
    }

    /** Shows the viewer modally, starting at the first image. {@code images} must be non-empty. */
    public void show(String title, List<Path> images, LocalDate date) {
        int[] index = {0};
        Map<Path, ExifInfo> exifCache = new HashMap<>();

        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(IMAGE_W);
        imageView.setFitHeight(IMAGE_H);
        // Wrapping the ImageView in a fixed-size, centered box keeps the layout footprint
        // constant at 640x480 regardless of the photo's aspect ratio, so nothing above or
        // below it ever moves between images.
        StackPane imageBox = new StackPane(imageView);
        imageBox.setAlignment(Pos.CENTER);
        imageBox.setMinSize(IMAGE_W, IMAGE_H);
        imageBox.setPrefSize(IMAGE_W, IMAGE_H);
        imageBox.setMaxSize(IMAGE_W, IMAGE_H);
        Label counter = new Label();

        Label pathLabel = new Label();
        pathLabel.setStyle("-fx-font-weight: bold;");
        pathLabel.setWrapText(false);
        // Center ellipsis keeps a bit of the directory (start) and the full filename tail
        // (end) visible, cutting only the middle — and capping the width well below the
        // 640px image width keeps the line short instead of stretching to fill it.
        pathLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        pathLabel.setAlignment(Pos.CENTER);
        pathLabel.setMaxWidth(420);

        Button copyDirBtn = new Button("📋");
        copyDirBtn.setTooltip(new Tooltip("Copy directory to clipboard"));
        copyDirBtn.setOnAction(ev -> {
            Clipboards.putString(images.get(index[0]).getParent().toString());
            statusSink.status("Directory copied to clipboard");
        });

        HBox pathRow = new HBox(6, pathLabel, copyDirBtn);
        pathRow.setAlignment(Pos.CENTER);
        pathRow.setMaxWidth(IMAGE_W);
        pathRow.setMinWidth(IMAGE_W);
        pathRow.setPrefWidth(IMAGE_W);

        Label metaLabel = new Label();
        // Fixed width matching the image, with ellipsis instead of wrapping/growing — otherwise
        // a longer/shorter EXIF string changes the label's preferred width on every navigation,
        // which grows/shrinks the whole dialog.
        metaLabel.setMaxWidth(IMAGE_W);
        metaLabel.setMinWidth(IMAGE_W);
        metaLabel.setPrefWidth(IMAGE_W);
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

        Button firstBtn = new Button("⏮");
        Button prevBtn = new Button("◀");
        Button nextBtn = new Button("▶");
        Button lastBtn = new Button("⏭");

        ButtonType mapButtonType = new ButtonType("Open in Maps", ButtonBar.ButtonData.RIGHT);

        Dialog<Void> dlg = new Dialog<>();
        dlg.setResizable(true);
        dlg.getDialogPane().getStyleClass().add("image-viewer-dialog");
        dlg.getDialogPane().getButtonTypes().add(mapButtonType);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Button mapButton = (Button) dlg.getDialogPane().lookupButton(mapButtonType);
        Tooltip mapTooltip = new Tooltip();
        Tooltip.install(mapButton, mapTooltip);

        Runnable refresh = () -> {
            Path p = images.get(index[0]);
            imageView.setImage(new Image(p.toUri().toString(), IMAGE_W, IMAGE_H, true, true, true));
            counter.setText((index[0] + 1) + " / " + images.size());
            firstBtn.setDisable(index[0] == 0);
            prevBtn.setDisable(index[0] == 0);
            nextBtn.setDisable(index[0] == images.size() - 1);
            lastBtn.setDisable(index[0] == images.size() - 1);

            pathLabel.setText(UiText.homeRelative(p));
            ExifInfo exif = exifCache.computeIfAbsent(p, exifReader::read);
            metaLabel.setText(describeExif(exif));

            mapButton.setDisable(!exif.hasLocation());
            mapTooltip.setText(exif.hasLocation() ? "Open coordinates in Google Maps" : "No geo data");
        };
        firstBtn.setOnAction(ev -> { index[0] = 0; refresh.run(); });
        prevBtn.setOnAction(ev -> { if (index[0] > 0) index[0]--; refresh.run(); });
        nextBtn.setOnAction(ev -> { if (index[0] < images.size() - 1) index[0]++; refresh.run(); });
        lastBtn.setOnAction(ev -> { index[0] = images.size() - 1; refresh.run(); });

        mapButton.addEventFilter(ActionEvent.ACTION, ev -> {
            ExifInfo exif = exifCache.get(images.get(index[0]));
            if (exif != null && exif.hasLocation()) {
                browser.open(exif.mapsUrl());
            }
            ev.consume();
        });

        refresh.run();

        HBox nav = new HBox(8, firstBtn, prevBtn, counter, nextBtn, lastBtn);
        nav.setAlignment(Pos.CENTER);
        nav.setPadding(new Insets(0, 0, 4, 0));
        VBox content = new VBox(4, topInfo, imageBox, nav);
        content.setAlignment(Pos.CENTER);
        content.setMinWidth(IMAGE_W);
        content.setPrefWidth(IMAGE_W);
        content.setMaxWidth(IMAGE_W);
        // Lock the dialog's initial width so it can't grow/shrink between images — the content
        // is fixed at 640, the dialog pane just adds its own padding. Still user-resizable
        // afterwards since we only set the preferred width.
        dlg.getDialogPane().setPrefWidth(680);

        // Date is shown in the window title only — it's redundant above the image and was
        // taking up a whole header row of vertical space.
        dlg.setTitle(title + " of " + UiText.friendlyDate(date));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.LEFT) {
                if (index[0] > 0) { index[0]--; refresh.run(); }
                ev.consume();
            } else if (ev.getCode() == KeyCode.RIGHT) {
                if (index[0] < images.size() - 1) { index[0]++; refresh.run(); }
                ev.consume();
            }
        });
        Dialogs.applyStylesheet(dlg.getDialogPane());
        dlg.showAndWait();
    }

    /** One-line EXIF summary: camera · aperture · ISO · exposure · dimensions · coordinates. */
    private static String describeExif(ExifInfo exif) {
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
        sb.append(exif.hasLocation()
                ? " · 📍 " + Coordinates.toDdm(exif.latitude(), exif.longitude())
                : " · 📍 Uncharted");
        return sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null) return;
        if (sb.length() > 0) sb.append(" · ");
        sb.append(part);
    }
}
