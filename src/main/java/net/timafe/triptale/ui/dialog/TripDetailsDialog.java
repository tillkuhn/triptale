package net.timafe.triptale.ui.dialog;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.ui.Dialogs;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Read-only trip statistics plus editable name and description. Returns the edited trip, or
 * empty when the user cancelled or changed nothing — the caller persists it.
 */
public final class TripDetailsDialog {

    private final MarkdownStore store;

    public TripDetailsDialog(MarkdownStore store) {
        this.store = store;
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

    private Stats collectStats(Trip trip) {
        List<LocalDate> dates = store.listEntryDates(trip.ref());
        double totalDistance = 0;
        double totalAltitude = 0;
        int activeDays = 0;
        int activeAltitudeDays = 0;
        for (LocalDate d : dates) {
            DiaryEntry e = store.loadEntry(trip.ref(), d);
            if (e.distance() != null && e.distance() > 0) {
                totalDistance += e.distance();
                activeDays++;
            }
            if (e.altitudeMeters() != null && e.altitudeMeters() > 0) {
                totalAltitude += e.altitudeMeters();
                activeAltitudeDays++;
            }
        }
        return new Stats(dates.size(), totalDistance, activeDays, totalAltitude, activeAltitudeDays);
    }

    /** Empty when cancelled or when neither name nor description changed. */
    public Optional<Trip> showAndWait(Trip trip) {
        Stats stats = collectStats(trip);
        String originalDesc = trip.description() == null ? "" : trip.description();

        TextField nameField = new TextField(trip.name());
        nameField.setPromptText("Trip name");
        nameField.setPrefColumnCount(28);
        TextArea descArea = new TextArea(originalDesc);
        descArea.setWrapText(true);
        descArea.setPrefRowCount(4);
        descArea.setPrefColumnCount(40);

        GridPane grid = Dialogs.formGrid();
        int row = 0;
        grid.add(new Label("Name:"), 0, row);
        grid.add(nameField, 1, row++);
        grid.add(new Label("Slug:"), 0, row);
        grid.add(new Label(trip.slug()), 1, row++);
        grid.add(new Label("Year:"), 0, row);
        grid.add(new Label(Integer.toString(trip.year())), 1, row++);
        grid.add(new Label("Start date:"), 0, row);
        grid.add(new Label(trip.startDate() == null ? "—" : trip.startDate().toString()), 1, row++);
        grid.add(new Label("Entries:"), 0, row);
        grid.add(new Label(Integer.toString(stats.entries())), 1, row++);
        grid.add(new Label("Total distance:"), 0, row);
        grid.add(new Label(stats.distanceText()), 1, row++);
        grid.add(new Label("Total altitude:"), 0, row);
        grid.add(new Label(stats.altitudeText()), 1, row++);
        grid.add(new Label("Description:"), 0, row);
        grid.add(descArea, 1, row);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Trip Details");
        dlg.setHeaderText(trip.name());
        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        Node okButton = dlg.getDialogPane().lookupButton(ButtonType.OK);
        Runnable refreshOk = () -> okButton.setDisable(
                nameField.getText().isBlank()
                        || (nameField.getText().trim().equals(trip.name())
                            && descArea.getText().equals(originalDesc)));
        refreshOk.run();
        nameField.textProperty().addListener((o, a, b) -> refreshOk.run());
        descArea.textProperty().addListener((o, a, b) -> refreshOk.run());

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return Optional.empty();
        String newName = nameField.getText().trim();
        String newDesc = descArea.getText();
        if (newName.equals(trip.name()) && newDesc.equals(originalDesc)) return Optional.empty();
        // Slug and year are immutable — a rename never moves the directory.
        return Optional.of(new Trip(trip.year(), trip.slug(), newName, trip.startDate(), newDesc));
    }
}
