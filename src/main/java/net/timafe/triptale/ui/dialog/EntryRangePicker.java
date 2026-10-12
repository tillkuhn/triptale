package net.timafe.triptale.ui.dialog;

import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.ui.UiText;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The From/To combos over a trip's entries ("Day 2: Frankfurt to Mannheim"), shared by Export
 * Diary and Map Assistant. Moving one end past the other drags the other along. "All days"
 * switches to the whole trip and back to the range picked before.
 */
final class EntryRangePicker {

    /** Caps each combo; otherwise it grows to fit its longest label. Longer labels are ellipsized. */
    private static final double COMBO_WIDTH = 300;

    private final ComboBox<LocalDate> fromCombo = new ComboBox<>();
    private final ComboBox<LocalDate> toCombo = new ComboBox<>();
    private final CheckBox allDays = new CheckBox("All days");
    private final List<LocalDate> dates;
    // The range before "All days" was checked, restored when it's unchecked
    private LocalDate savedFrom;
    private LocalDate savedTo;
    private final HBox node;

    /** {@code entries} in date order; a null or unknown {@code from}/{@code to} means the first/last entry. */
    EntryRangePicker(Trip trip, List<DiaryEntry> entries, LocalDate from, LocalDate to) {
        Map<LocalDate, String> labels = new HashMap<>();
        for (DiaryEntry e : entries) labels.put(e.date(), entryLabel(trip, e));
        dates = entries.stream().map(DiaryEntry::date).toList();
        StringConverter<LocalDate> converter = new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : labels.getOrDefault(d, d.toString()); }
            @Override public LocalDate fromString(String s) { return null; }
        };
        for (ComboBox<LocalDate> c : List.of(fromCombo, toCombo)) {
            c.getItems().setAll(dates);
            c.setConverter(converter);
            c.setDisable(dates.isEmpty());
            c.setPrefWidth(COMBO_WIDTH);
            c.setMaxWidth(COMBO_WIDTH);
        }
        if (!dates.isEmpty()) {
            fromCombo.setValue(from != null && dates.contains(from) ? from : dates.getFirst());
            toCombo.setValue(to != null && dates.contains(to) ? to : dates.getLast());
        }
        fromCombo.valueProperty().addListener((obs, old, d) -> {
            if (d != null && toCombo.getValue() != null && d.isAfter(toCombo.getValue())) toCombo.setValue(d);
        });
        toCombo.valueProperty().addListener((obs, old, d) -> {
            if (d != null && fromCombo.getValue() != null && d.isBefore(fromCombo.getValue())) fromCombo.setValue(d);
        });
        allDays.setDisable(dates.size() < 2);
        allDays.setSelected(dates.size() > 1 && coversAll());
        allDays.setOnAction(e -> toggleAllDays());
        // Narrowing the range by hand leaves "All days" mode
        fromCombo.valueProperty().addListener((obs, old, d) -> allDays.setSelected(coversAll()));
        toCombo.valueProperty().addListener((obs, old, d) -> allDays.setSelected(coversAll()));
        node = new HBox(8, new Label("From:"), fromCombo, new Label("To:"), toCombo, allDays);
        node.setAlignment(Pos.CENTER_LEFT);
    }

    private void toggleAllDays() {
        if (allDays.isSelected()) {
            savedFrom = fromCombo.getValue();
            savedTo = toCombo.getValue();
            // To first: moving From to the first entry never drags To along
            toCombo.setValue(dates.getLast());
            fromCombo.setValue(dates.getFirst());
        } else if (savedFrom != null && savedTo != null) {
            fromCombo.setValue(savedFrom);
            toCombo.setValue(savedTo);
        } else {
            // Opened on the whole trip: there is no earlier range, so fall back to the last day
            fromCombo.setValue(dates.getLast());
        }
        allDays.setSelected(coversAll());
    }

    private boolean coversAll() {
        return !dates.isEmpty() && dates.getFirst().equals(fromCombo.getValue()) && dates.getLast().equals(toCombo.getValue());
    }

    HBox node() {
        return node;
    }

    LocalDate from() {
        return fromCombo.getValue();
    }

    LocalDate to() {
        return toCombo.getValue();
    }

    /** Runs on every change of either end, including one end dragged along by the other. */
    void onChange(Runnable action) {
        fromCombo.valueProperty().addListener((obs, old, d) -> action.run());
        toCombo.valueProperty().addListener((obs, old, d) -> action.run());
    }

    /** "Day 2: Frankfurt to Mannheim"; the date stands in for a missing day number or title. */
    private static String entryLabel(Trip trip, DiaryEntry e) {
        Long day = trip.dayNumber(e.date());
        String title = e.title() == null || e.title().isBlank() || e.title().equals(DiaryEntry.DEFAULT_TITLE)
                ? UiText.friendlyDate(e.date()) : e.title();
        return (day == null ? e.date().toString() : "Day " + day) + ": " + title;
    }
}
