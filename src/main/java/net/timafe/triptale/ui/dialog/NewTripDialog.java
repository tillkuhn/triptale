package net.timafe.triptale.ui.dialog;

import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import net.timafe.triptale.domain.TripRef;
import net.timafe.triptale.util.Slugs;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Dialog;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.ui.ActivityChoices;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.util.GpxImport;

import java.io.File;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Collects the fields for a new trip. Returns the raw user input — slug derivation, the
 * already-exists check and persistence stay with the caller.
 */
public final class NewTripDialog {

    private final MarkdownStore store;

    public NewTripDialog(MarkdownStore store) {
        this.store = store;
    }

    /** {@code activity} is the trip's default for new entries, null for none. */
    public record Spec(String name, String slug, LocalDate startDate, String activity, String postTags, String description, Optional<FirstEntry> firstEntry) {}

    /**
     * All fields but {@code title} are null unless the trip was created from a GPX import;
     * {@code gpxFile} is then the source file, to be saved as an attachment.
     */
    public record FirstEntry(String title, Double lat, Double lon, Double stopLat, Double stopLon,
                              Double distanceKm, Double altitudeGainM, File gpxFile) {}

    /** Empty when the user cancelled. */
    public Optional<Spec> showAndWait() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("New Trip");
        dlg.setHeaderText("Create a new trip");

        TextField nameField = new TextField();
        nameField.setPromptText("Trip name");
        nameField.setPrefColumnCount(28);
        DatePicker startField = new DatePicker(LocalDate.now());
        startField.setConverter(Dialogs.isoDateConverter());
        ComboBox<String> activityCombo = ActivityChoices.newComboBox(null);
        TextField postTagsField = new TextField();
        postTagsField.setPromptText("WordPress tags, semicolon-separated");
        postTagsField.setTooltip(new Tooltip("Only a starting suggestion for the tags of a new Post"));
        TextArea descArea = new TextArea();
        descArea.setPromptText("Optional description");
        descArea.setPrefRowCount(3);
        descArea.setPrefColumnCount(28);
        descArea.setWrapText(true);

        Label importHintLabel = new Label();
        importHintLabel.getStyleClass().add("hint-label");

        CheckBox initFirstEntryCheck = new CheckBox("Init first Tale Entry on trip creation");

        // GPX-derived title/coords, kept separate from the (possibly later edited) Name field.
        String[] gpxName = new String[1];
        Double[] gpxLat = new Double[1];
        Double[] gpxLon = new Double[1];
        Double[] gpxStopLat = new Double[1];
        Double[] gpxStopLon = new Double[1];
        Double[] gpxDistanceKm = new Double[1];
        Double[] gpxAltitudeGainM = new Double[1];
        File[] gpxFile = new File[1];

        GridPane grid = Dialogs.formGrid();
        TextField slugField = new TextField();
        slugField.setPromptText("Folder name");
        slugField.setTooltip(new Tooltip("Folder name of the trip. Follows the name until edited; fixed once the trip is created"));
        Button slugResetButton = new Button("↺");
        slugResetButton.setTooltip(new Tooltip("Derive the slug from the name again"));
        HBox.setHgrow(slugField, Priority.ALWAYS);
        HBox slugBox = new HBox(6, slugField, slugResetButton);
        Label slugHintLabel = new Label();
        slugHintLabel.getStyleClass().add("hint-label");

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Slug:"), 0, 1);
        grid.add(slugBox, 1, 1);
        grid.add(slugHintLabel, 1, 2);
        grid.add(new Label("Start date:"), 0, 3);
        grid.add(startField, 1, 3);
        grid.add(new Label("Default activity:"), 0, 4);
        grid.add(activityCombo, 1, 4);
        grid.add(new Label("Default post tags:"), 0, 5);
        grid.add(postTagsField, 1, 5);
        grid.add(new Label("Description:"), 0, 6);
        grid.add(descArea, 1, 6);
        grid.add(initFirstEntryCheck, 1, 7);
        grid.add(importHintLabel, 1, 8);

        dlg.getDialogPane().setContent(grid);
        ButtonType importType = new ButtonType("Import GPX…", ButtonBar.ButtonData.LEFT);
        dlg.getDialogPane().getButtonTypes().addAll(importType, ButtonType.OK, ButtonType.CANCEL);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        Node importButtonNode = dlg.getDialogPane().lookupButton(importType);
        importButtonNode.addEventFilter(ActionEvent.ACTION, ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Import GPX");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("GPX files", "*.gpx"));
            store.loadLastGpxFolder().ifPresent(folder -> chooser.setInitialDirectory(folder.toFile()));
            var file = chooser.showOpenDialog(dlg.getDialogPane().getScene().getWindow());
            if (file != null) {
                store.saveLastGpxFolder(file.getParentFile().toPath());
                Optional<GpxImport.Parsed> parsed = GpxImport.parse(file);
                if (parsed.isEmpty()) {
                    importHintLabel.setText("Couldn't parse a track name/point from this file.");
                } else {
                    GpxImport.Parsed p = parsed.get();
                    nameField.setText(p.name());
                    startField.setValue(p.date());
                    gpxName[0] = p.name();
                    gpxLat[0] = p.lat();
                    gpxLon[0] = p.lon();
                    gpxStopLat[0] = p.stopLat();
                    gpxStopLon[0] = p.stopLon();
                    gpxDistanceKm[0] = p.distanceKm();
                    gpxAltitudeGainM[0] = p.altitudeGainM();
                    gpxFile[0] = file;
                    initFirstEntryCheck.setSelected(true);
                    importHintLabel.setText("Imported \"" + p.name() + "\"");
                }
            }
            ev.consume();
        });

        Node okButton = dlg.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        boolean[] autoSlug = {true};
        boolean[] settingSlug = {false};
        Runnable refreshOk = () -> {
            String slug = slugField.getText().trim();
            String problem = null;
            if (!nameField.getText().isBlank() || !slug.isEmpty()) {
                if (!Slugs.isValid(slug)) {
                    problem = "Slug: letters, digits and _ only";
                } else if (startField.getValue() != null
                        && store.tripExists(new TripRef(startField.getValue().getYear(), slug))) {
                    problem = "A trip with this slug already exists in " + startField.getValue().getYear();
                }
            }
            boolean invalid = problem != null;
            slugHintLabel.setText(problem == null ? "" : problem);
            slugField.pseudoClassStateChanged(PseudoClass.getPseudoClass("error"), invalid);
            okButton.setDisable(nameField.getText().isBlank() || startField.getValue() == null
                    || !Slugs.isValid(slug) || invalid);
        };
        Runnable deriveSlug = () -> {
            settingSlug[0] = true;
            slugField.setText(Slugs.toSlugOrEmpty(nameField.getText()));
            settingSlug[0] = false;
        };
        nameField.textProperty().addListener((o, a, b) -> {
            if (autoSlug[0]) deriveSlug.run();
            refreshOk.run();
        });
        slugField.textProperty().addListener((o, a, b) -> {
            if (!settingSlug[0]) autoSlug[0] = false;
            refreshOk.run();
        });
        slugResetButton.setOnAction(e -> {
            autoSlug[0] = true;
            deriveSlug.run();
            refreshOk.run();
        });
        startField.valueProperty().addListener((o, a, b) -> refreshOk.run());

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return Optional.empty();

        String name = nameField.getText().trim();
        Optional<FirstEntry> firstEntry = Optional.empty();
        if (initFirstEntryCheck.isSelected()) {
            firstEntry = Optional.of(gpxName[0] != null
                    ? new FirstEntry(gpxName[0], gpxLat[0], gpxLon[0], gpxStopLat[0], gpxStopLon[0],
                            gpxDistanceKm[0], gpxAltitudeGainM[0], gpxFile[0])
                    : new FirstEntry(name, null, null, null, null, null, null, null));
        }
        return Optional.of(new Spec(name, slugField.getText().trim(), startField.getValue(), ActivityChoices.value(activityCombo),
                postTagsField.getText(), descArea.getText(), firstEntry));
    }
}
