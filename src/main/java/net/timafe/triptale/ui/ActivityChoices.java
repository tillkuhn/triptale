package net.timafe.triptale.ui;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import net.timafe.triptale.domain.Activity;

import java.util.ArrayList;
import java.util.List;

/**
 * The activity picker shared by the entry form and the trip dialogs (todos 55, 57): "not set",
 * the curated {@link Activity} values, and an unknown value as an extra item so it's never dropped.
 */
public final class ActivityChoices {

    /** Item for "not set"; a null combo value is awkward to render. {@link #value} maps it back to null. */
    private static final String NONE = "";

    private ActivityChoices() {}

    public static ComboBox<String> newComboBox(String activity) {
        ComboBox<String> combo = new ComboBox<>();
        install(combo);
        show(combo, activity);
        return combo;
    }

    public static void install(ComboBox<String> combo) {
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(String a) {
                return a == null || a.isEmpty() ? "😴 No activity yet" : Activity.emojiFor(a) + " " + Activity.labelFor(a);
            }
            @Override public String fromString(String s) { return null; }
        });
        show(combo, null);
    }

    public static void show(ComboBox<String> combo, String activity) {
        List<String> items = new ArrayList<>();
        items.add(NONE);
        for (Activity a : Activity.values()) items.add(a.key());
        if (activity != null && Activity.of(activity).isEmpty()) items.add(activity);
        combo.getItems().setAll(items);
        combo.setValue(activity == null ? NONE : activity);
    }

    /** The selected activity, null for "not set". */
    public static String value(ComboBox<String> combo) {
        return Activity.normalize(combo.getValue());
    }

    private static final String STORED = "trackTotalsStored";

    /**
     * The "Track Totals" checkbox (todo 96) next to {@code combo}: disabled and shown unchecked
     * while no activity is set, but the flag the user chose is remembered and comes back with an
     * activity. Read it with {@link #trackTotals}.
     */
    public static CheckBox newTrackTotalsCheck(ComboBox<String> combo, boolean stored) {
        CheckBox check = new CheckBox("Track Totals");
        check.setTooltip(new Tooltip("Totals (export, editor, trip details) count only days with the default activity"));
        check.getProperties().put(STORED, stored);
        Runnable sync = () -> {
            boolean hasActivity = value(combo) != null;
            check.setDisable(!hasActivity);
            check.setSelected(hasActivity && (Boolean) check.getProperties().get(STORED));
        };
        check.selectedProperty().addListener((o, a, b) -> {
            if (!check.isDisabled()) check.getProperties().put(STORED, b);
        });
        combo.valueProperty().addListener((o, a, b) -> sync.run());
        sync.run();
        return check;
    }

    /** The remembered flag, which only takes effect with an activity (see {@code Trip#tracksTotals}). */
    public static boolean trackTotals(CheckBox check) {
        return (Boolean) check.getProperties().get(STORED);
    }
}
