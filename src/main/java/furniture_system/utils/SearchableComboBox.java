package furniture_system.utils;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import java.util.List;
import java.util.function.Function;

/**
 * SearchableComboBox – a drop-in helper that adds real-time search filtering
 * to any ComboBox containing entity objects.
 *
 * Usage:
 * <pre>
 *   // 1. Declare a regular ComboBox
 *   ComboBox<Customer> cbCustomer = new ComboBox<>();
 *
 *   // 2. Wrap it – returns a VBox (TextField + ComboBox)
 *   VBox customerField = SearchableComboBox.wrap(
 *       cbCustomer,
 *       customerDAO.findAll(),
 *       c -> c.getFullName() + " - " + c.getPhone()
 *   );
 *   grid.add(customerField, 1, row++);
 *
 *   // 3. Retrieve the selected value as usual
 *   Customer selected = cbCustomer.getValue();
 * </pre>
 *
 * Features:
 *  - A TextField sits above the ComboBox and filters items in real time (case-insensitive)
 *  - If the filter returns exactly 1 result, that item is auto-selected
 *  - Clearing the TextField restores the full list while preserving the current selection
 *  - Items are displayed using the provided displayFn; search also matches against it
 *  - The type and public API of the original ComboBox are unchanged
 */
public class SearchableComboBox {

    /**
     * Wraps a ComboBox with a search TextField placed above it.
     *
     * @param comboBox  The ComboBox to enhance (already declared by the caller)
     * @param fullList  The complete list of items
     * @param displayFn Function that converts an item to its display/search string
     * @param <T>       The entity type
     * @return A VBox containing [TextField, ComboBox] to be added to the layout
     */
    public static <T> VBox wrap(ComboBox<T> comboBox,
                                List<T> fullList,
                                Function<T, String> displayFn) {

        ObservableList<T> allItems = FXCollections.observableArrayList(fullList);

        // Configure cell factory to render items using displayFn instead of toString()
        comboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : displayFn.apply(item));
            }
        });
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : displayFn.apply(item));
            }
        });

        // Load full list initially
        comboBox.setItems(allItems);
        comboBox.setMaxWidth(Double.MAX_VALUE);

        // Search TextField
        TextField searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.setStyle(
            "-fx-background-color:#f8fafc;" +
            "-fx-border-color:#e2e8f0;" +
            "-fx-border-width:1;" +
            "-fx-border-radius:6 6 0 0;" +
            "-fx-background-radius:6 6 0 0;" +
            "-fx-padding:6 10;" +
            "-fx-font-size:12px;"
        );
        comboBox.setStyle(
            "-fx-border-radius:0 0 6 6;" +
            "-fx-background-radius:0 0 6 6;"
        );

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String kw = newVal == null ? "" : newVal.trim().toLowerCase();

            if (kw.isEmpty()) {
                // Input cleared: restore full list and preserve current selection
                T selected = comboBox.getValue();
                comboBox.setItems(FXCollections.observableArrayList(allItems));
                comboBox.setValue(selected);
                return;
            }

            // Filter items whose display string contains the keyword
            ObservableList<T> filtered = FXCollections.observableArrayList(
                allItems.filtered(item -> {
                    if (item == null) return false;
                    return displayFn.apply(item).toLowerCase().contains(kw);
                })
            );
            comboBox.setItems(filtered);

            // Auto-select when exactly one result remains
            if (filtered.size() == 1) {
                comboBox.setValue(filtered.get(0));
            } else if (!filtered.isEmpty()) {
                // Open dropdown so the user can see the filtered results
                if (!comboBox.isShowing()) comboBox.show();
            }
        });

        VBox box = new VBox(0, searchField, comboBox);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /**
     * Simplified overload that uses each item's toString() as the display string.
     */
    public static <T> VBox wrap(ComboBox<T> comboBox, List<T> fullList) {
        return wrap(comboBox, fullList, item -> item != null ? item.toString() : "");
    }

    /**
     * Convenience factory: creates a new ComboBox and wraps it in one call.
     * Returns a two-element array: [ComboBox&lt;T&gt;, VBox].
     *
     * <pre>
     *   Object[] result = SearchableComboBox.create(customers,
     *       c -> c.getFullName() + " - " + c.getPhone());
     *   ComboBox&lt;Customer&gt; cbCustomer = (ComboBox&lt;Customer&gt;) result[0];
     *   grid.add((VBox) result[1], 1, row++);
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public static <T> Object[] create(List<T> fullList, Function<T, String> displayFn) {
        ComboBox<T> cb = new ComboBox<>();
        VBox box = wrap(cb, fullList, displayFn);
        return new Object[]{ cb, box };
    }
}
