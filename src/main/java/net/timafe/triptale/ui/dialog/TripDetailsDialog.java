package net.timafe.triptale.ui.dialog;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.HBox;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.ui.ActivityChoices;
import net.timafe.triptale.ui.Dialogs;

import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.domain.Activity;
import net.timafe.triptale.domain.LinkKind;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only trip statistics plus editable name and description. Returns the edited trip, or
 * empty when the user cancelled or changed nothing — the caller persists it.
 */
public final class TripDetailsDialog {

    private final MarkdownStore store;
    private final AttachmentsDir attachmentsDir;

    public TripDetailsDialog(MarkdownStore store, AttachmentsDir attachmentsDir) {
        this.store = store;
        this.attachmentsDir = attachmentsDir;
    }

    /**
     * Aggregated distance/altitude over a trip's entries. "Active" days are those with a
     * recorded value greater than zero, so rest days don't drag the averages down.
     */
    private record Stats(int entries, double totalDistance, int activeDays,
                         double totalAltitude, int activeAltitudeDays) {

        String distanceText() {
            return activeDays > 0
                    ? String.format(Locale.ROOT, "%.1f km (avg %.1f km / active day, %d days)",
                            totalDistance, totalDistance / activeDays, activeDays)
                    : String.format(Locale.ROOT, "%.1f km", totalDistance);
        }

        String altitudeText() {
            return activeAltitudeDays > 0
                    ? String.format(Locale.ROOT, "%.0f m (avg %.0f m / active day, %d days)",
                            totalAltitude, totalAltitude / activeAltitudeDays, activeAltitudeDays)
                    : String.format(Locale.ROOT, "%.0f m", totalAltitude);
        }
    }

    private Stats collectStats(Trip trip, List<DiaryEntry> entries) {
        double totalDistance = 0;
        double totalAltitude = 0;
        int activeDays = 0;
        int activeAltitudeDays = 0;
        for (DiaryEntry e : entries) {
            if (e.distance() != null && e.distance() > 0) {
                totalDistance += e.distance();
                activeDays++;
            }
            if (e.altitudeMeters() != null && e.altitudeMeters() > 0) {
                totalAltitude += e.altitudeMeters();
                activeAltitudeDays++;
            }
        }
        return new Stats(entries.size(), totalDistance, activeDays, totalAltitude, activeAltitudeDays);
    }

    /**
     * Outcome of the dialog: {@code trip} is the edited trip (null when unchanged), {@code openDate}
     * the entry the user asked to open (null for none). Cancel yields {@link Optional#empty()}.
     */
    public record Result(Trip trip, LocalDate openDate) {}

    public Optional<Result> showAndWait(Trip trip) {
        List<LocalDate> dates = store.listEntryDates(trip.ref());
        List<DiaryEntry> entries = dates.stream().map(d -> store.loadEntry(trip.ref(), d)).toList();
        Stats stats = collectStats(trip, entries);
        String originalDesc = trip.description() == null ? "" : trip.description();
        LocalDate maxEntryDate = dates.isEmpty() ? null : dates.get(dates.size() - 1);

        TextField nameField = new TextField(trip.name());
        nameField.setPromptText("Trip name");
        nameField.setPrefColumnCount(28);
        TextArea descArea = new TextArea(originalDesc);
        descArea.setWrapText(true);
        descArea.setPrefRowCount(4);
        descArea.setPrefColumnCount(60);

        DatePicker endField = new DatePicker(trip.endDate());
        endField.setConverter(Dialogs.isoDateConverter());
        endField.setPromptText("open-ended");
        // DatePicker only commits typed text on Enter/focus loss; commit blank (reopen) and
        // complete dates right away so OK reflects the field as shown
        endField.getEditor().textProperty().addListener((o, a, text) -> {
            LocalDate typed = endField.getConverter().fromString(text);
            if ((typed != null || text.isBlank()) && !Objects.equals(typed, endField.getValue())) {
                endField.setValue(typed);
            }
        });
        Button lastEntryButton = new Button("Set to last entry");
        if (maxEntryDate != null) {
            lastEntryButton.setTooltip(new Tooltip("Set the end date to " + maxEntryDate));
        }
        lastEntryButton.setOnAction(ev -> endField.setValue(maxEntryDate));
        HBox dateBox = new HBox(10,
                new Label(trip.startDate() == null ? "—" : trip.startDate().toString()),
                endField, lastEntryButton);
        dateBox.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> activityCombo = ActivityChoices.newComboBox(trip.activity());

        TextField postTagsField = new TextField(trip.postTags() == null ? "" : trip.postTags());
        postTagsField.setPromptText("WordPress tags, semicolon-separated");
        postTagsField.setTooltip(new Tooltip("Only a starting suggestion for the tags of a new Post;"
                + " later changes here don't touch existing Posts"));

        GridPane grid = Dialogs.formGrid();
        int row = 0;
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(Region.USE_PREF_SIZE);
        grid.getColumnConstraints().add(labelCol);
        grid.add(new Label("Name:"), 0, row);
        grid.add(nameField, 1, row++);
        grid.add(new Label("Slug:"), 0, row);
        grid.add(new Label(trip.slug()), 1, row++);
        grid.add(new Label("Year:"), 0, row);
        grid.add(new Label(Integer.toString(trip.year())), 1, row++);
        grid.add(new Label("Start / end date:"), 0, row);
        grid.add(dateBox, 1, row++);
        grid.add(new Label("Default activity:"), 0, row);
        grid.add(activityCombo, 1, row++);
        grid.add(new Label("Default post tags:"), 0, row);
        grid.add(postTagsField, 1, row++);
        grid.add(new Label("Total distance:"), 0, row);
        grid.add(new Label(stats.distanceText()), 1, row++);
        grid.add(new Label("Total altitude:"), 0, row);
        grid.add(new Label(stats.altitudeText()), 1, row++);
        grid.add(new Label("Description:"), 0, row);
        grid.add(descArea, 1, row);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("📝 Trip Planner");
        dlg.setHeaderText(trip.year() + " · " + trip.name());
        TableView<DiaryEntry> table = overviewTable(trip, entries, backpackCounts(trip, entries), mapDates(trip, entries));
        Tab detailsTab = new Tab("Trip Details", grid);
        Tab overviewTab = new Tab("Tale Entries (" + stats.entries() + ")", table);
        TabPane tabs = new TabPane(detailsTab, overviewTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("settings-tabs");
        tabs.setPrefWidth(880);
        dlg.getDialogPane().setContent(tabs);
        ButtonType openType = new ButtonType("Open day", ButtonBar.ButtonData.LEFT);
        dlg.getDialogPane().getButtonTypes().addAll(openType, ButtonType.OK, ButtonType.CANCEL);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        Node okButton = dlg.getDialogPane().lookupButton(ButtonType.OK);
        java.util.function.BooleanSupplier formInvalid = () -> {
            LocalDate newEnd = endField.getValue();
            return nameField.getText().isBlank() || (newEnd != null
                    && ((trip.startDate() != null && newEnd.isBefore(trip.startDate()))
                            || (maxEntryDate != null && newEnd.isBefore(maxEntryDate))));
        };
        Runnable refreshOk = () -> {
            LocalDate newEnd = endField.getValue();
            lastEntryButton.setDisable(maxEntryDate == null || maxEntryDate.equals(newEnd));
            okButton.setDisable(
                    formInvalid.getAsBoolean()
                            || (nameField.getText().trim().equals(trip.name())
                                    && descArea.getText().equals(originalDesc)
                                    && Objects.equals(newEnd, trip.endDate())
                                    && Objects.equals(ActivityChoices.value(activityCombo), trip.activity())
                                    && Objects.equals(blankToNull(postTagsField.getText()), trip.postTags())));
        };
        Node openButton = dlg.getDialogPane().lookupButton(openType);
        // Open = apply valid edits + jump, so it needs a valid form (see refreshOk) and a selected row
        Runnable refreshOpen = () -> openButton.setDisable(
                table.getSelectionModel().isEmpty() || formInvalid.getAsBoolean());
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshOpen.run());
        endField.valueProperty().addListener((o, a, b) -> refreshOpen.run());
        nameField.textProperty().addListener((o, a, b) -> refreshOpen.run());
        refreshOpen.run();
        // Fire the button so the dialog's normal close path runs (and the disabled state is honoured)
        table.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2 && !openButton.isDisabled()) ((Button) openButton).fire();
        });
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER && !openButton.isDisabled()) {
                ((Button) openButton).fire();
                ev.consume();
            }
        });
        refreshOk.run();
        nameField.textProperty().addListener((o, a, b) -> refreshOk.run());
        descArea.textProperty().addListener((o, a, b) -> refreshOk.run());
        endField.valueProperty().addListener((o, a, b) -> refreshOk.run());
        activityCombo.valueProperty().addListener((o, a, b) -> refreshOk.run());
        postTagsField.textProperty().addListener((o, a, b) -> refreshOk.run());

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty()) return Optional.empty();
        boolean open = result.get() == openType;
        if (result.get() != ButtonType.OK && !open) return Optional.empty();
        LocalDate openDate = open ? table.getSelectionModel().getSelectedItem().date() : null;
        String newName = nameField.getText().trim();
        String newDesc = descArea.getText();
        LocalDate newEnd = endField.getValue();
        String newActivity = ActivityChoices.value(activityCombo);
        String newPostTags = blankToNull(postTagsField.getText());
        Trip edited = null;
        if (!(newName.equals(trip.name()) && newDesc.equals(originalDesc)
                && Objects.equals(newEnd, trip.endDate()) && Objects.equals(newActivity, trip.activity())
                && Objects.equals(newPostTags, trip.postTags()))) {
            // Slug and year are immutable — a rename never moves the directory.
            edited = new Trip(trip.year(), trip.slug(), newName, trip.startDate(), newEnd, newDesc, newActivity, newPostTags);
        }
        return edited == null && openDate == null ? Optional.empty() : Optional.of(new Result(edited, openDate));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Backpack file count per day, same number as the Backpack button; failures count as 0. */
    private java.util.Map<LocalDate, Integer> backpackCounts(Trip trip, List<DiaryEntry> entries) {
        java.util.Map<LocalDate, Integer> counts = new java.util.HashMap<>();
        for (DiaryEntry e : entries) {
            try {
                counts.put(e.date(), attachmentsDir.dayFiles(trip.ref(), e.date()).size());
            } catch (RuntimeException ex) {
                counts.put(e.date(), 0);
            }
        }
        return counts;
    }

    /** Days with a saved tripmap.png, same check as the Sgt. Mapper button; failures count as none. */
    private java.util.Set<LocalDate> mapDates(Trip trip, List<DiaryEntry> entries) {
        java.util.Set<LocalDate> dates = new java.util.HashSet<>();
        for (DiaryEntry e : entries) {
            try {
                if (java.nio.file.Files.isRegularFile(attachmentsDir.tripMapFile(trip.ref(), e.date()))) {
                    dates.add(e.date());
                }
            } catch (RuntimeException ex) {
                // unreadable: no marker
            }
        }
        return dates;
    }

    /** Read-only list of the trip's entries: day, title, activity, start place. */
    private static TableView<DiaryEntry> overviewTable(Trip trip, List<DiaryEntry> entries,
                                                       java.util.Map<LocalDate, Integer> backpack,
                                                       java.util.Set<LocalDate> maps) {
        TableView<DiaryEntry> table = new TableView<>();
        table.getItems().setAll(entries);
        table.setPrefHeight(380);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No entries yet"));

        TableColumn<DiaryEntry, String> day = new TableColumn<>("Day");
        day.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(dayText(trip, c.getValue())));
        day.setPrefWidth(120);
        TableColumn<DiaryEntry, String> title = new TableColumn<>("Title");
        title.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(titleText(c.getValue())));
        title.setPrefWidth(250);
        TableColumn<DiaryEntry, String> activity = new TableColumn<>("Activity");
        activity.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(activityText(c.getValue())));
        activity.setPrefWidth(130);
        TableColumn<DiaryEntry, String> place = new TableColumn<>("Start place");
        place.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().startPlace() == null ? "" : c.getValue().startPlace()));
        TableColumn<DiaryEntry, DiaryEntry> links = new TableColumn<>("🔗");
        links.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        links.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(DiaryEntry e, boolean empty) {
                super.updateItem(e, empty);
                int n = empty || e == null || e.links() == null ? 0 : e.links().size();
                setText(n == 0 ? null : Integer.toString(n));
                if (n == 0) {
                    setTooltip(null);
                } else {
                    setTooltip(new Tooltip(e.links().keySet().stream().sorted(LinkKind.ORDER)
                            .map(v -> LinkKind.emojiFor(v) + " " + v)
                            .collect(java.util.stream.Collectors.joining(", "))));
                }
            }
        });
        links.setPrefWidth(60);
        links.setMinWidth(60);
        links.setMaxWidth(60);
        links.setStyle("-fx-alignment: CENTER;");
        TableColumn<DiaryEntry, String> pack = new TableColumn<>("🎒");
        pack.setCellValueFactory(c -> {
            int n = backpack.getOrDefault(c.getValue().date(), 0);
            return new javafx.beans.property.SimpleStringProperty(n == 0 ? "" : Integer.toString(n));
        });
        pack.setPrefWidth(60);
        pack.setMinWidth(60);
        pack.setMaxWidth(60);
        pack.setStyle("-fx-alignment: CENTER;");
        TableColumn<DiaryEntry, String> map = new TableColumn<>("🗺");
        map.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                maps.contains(c.getValue().date()) ? "✓" : ""));
        map.setPrefWidth(50);
        map.setMinWidth(50);
        map.setMaxWidth(50);
        map.setStyle("-fx-alignment: CENTER;");
        TableColumn<DiaryEntry, String> weather = new TableColumn<>("⛅");
        weather.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().weather() == null ? "" : c.getValue().weather().tempMax() + "°"));
        weather.setPrefWidth(55);
        weather.setMinWidth(55);
        weather.setMaxWidth(55);
        weather.setStyle("-fx-alignment: CENTER;");
        table.getColumns().addAll(day, title, activity, place, weather, links, pack, map);
        return table;
    }

    private static String dayText(Trip trip, DiaryEntry e) {
        String date = shortDate(e.date());
        if (trip.startDate() == null) return date;
        long n = ChronoUnit.DAYS.between(trip.startDate(), e.date()) + 1;
        return n >= 1 ? "(" + n + ") " + date : date;
    }

    /** "Oct 1st", English; the year is shown in the dialog header instead. */
    static String shortDate(LocalDate d) {
        int n = d.getDayOfMonth();
        String suffix = (n >= 11 && n <= 13) ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + n + suffix;
    }

    private static String titleText(DiaryEntry e) {
        String t = e.title();
        return t == null || t.isBlank() || t.equals(DiaryEntry.DEFAULT_TITLE) ? "" : t;
    }

    private static String activityText(DiaryEntry e) {
        String a = e.activity();
        return a == null || a.isEmpty() ? "" : Activity.emojiFor(a) + " " + Activity.labelFor(a);
    }
}
