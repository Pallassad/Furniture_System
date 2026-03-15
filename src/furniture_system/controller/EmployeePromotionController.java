package furniture_system.controller;

import furniture_system.model.Promotion;
import furniture_system.service.PromotionService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Employee – Promotion View (4.5.1)
 *
 * Responsibilities:
 *  - Display all currently ACTIVE promotions so the employee can see
 *    available codes before creating an order.
 *  - Provide a "lookup" field: employee types a code → system validates
 *    and shows whether it is usable (delegates actual discount calc to
 *    OrderService / EmployeeOrderController).
 *
 * The Employee CANNOT create, edit, or disable promotions.
 * The actual discount application happens inside EmployeeOrderController
 * (cbPromo ComboBox + recalcDraftTotals), which already uses
 * PromotionDAO.findActive(). This controller is a companion "reference"
 * panel — shown alongside the order form or as a standalone tab.
 */
public class EmployeePromotionController implements Initializable {

    // ── Table ─────────────────────────────────────────────────────────────────

    @FXML private TableView<Promotion>               tableView;
    @FXML private TableColumn<Promotion, String>     colCode;
    @FXML private TableColumn<Promotion, String>     colName;
    @FXML private TableColumn<Promotion, String>     colType;
    @FXML private TableColumn<Promotion, String>     colValue;
    @FXML private TableColumn<Promotion, String>     colMin;
    @FXML private TableColumn<Promotion, String>     colDates;
    @FXML private TableColumn<Promotion, String>     colUsage;

    // ── Code lookup ───────────────────────────────────────────────────────────

    @FXML private TextField tfLookupCode;
    @FXML private Label     lblLookupResult;

    // ── Info labels ───────────────────────────────────────────────────────────

    @FXML private Label lblCount;
    @FXML private Label lblMessage;

    // ── State ─────────────────────────────────────────────────────────────────

    private final PromotionService           svc  = new PromotionService();
    private final ObservableList<Promotion>  data = FXCollections.observableArrayList();
    private final DateTimeFormatter          dtf  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initTable();
        loadActivePromotions();
    }

    // ── Table setup ───────────────────────────────────────────────────────────

    private void initTable() {
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colType.setCellValueFactory(new PropertyValueFactory<>("discountType"));

        colValue.setCellValueFactory(cd -> {
            Promotion p = cd.getValue();
            String val = "PERCENT".equals(p.getDiscountType())
                    ? String.format("%.0f%%", p.getDiscountValue())
                    : String.format("%,.0f ₫", p.getDiscountValue());
            return new SimpleStringProperty(val);
        });

        colMin.setCellValueFactory(cd -> {
            Promotion p = cd.getValue();
            String min = p.getMinOrderValue() != null
                    ? String.format("%,.0f ₫", p.getMinOrderValue())
                    : "—";
            return new SimpleStringProperty(min);
        });

        colDates.setCellValueFactory(cd -> {
            Promotion p = cd.getValue();
            String s = p.getStartDate() != null ? p.getStartDate().format(dtf) : "?";
            String e = p.getEndDate()   != null ? p.getEndDate().format(dtf)   : "?";
            return new SimpleStringProperty(s + " → " + e);
        });

        colUsage.setCellValueFactory(cd -> {
            Promotion p = cd.getValue();
            String limit = p.getUsageLimit() != null ? String.valueOf(p.getUsageLimit()) : "∞";
            return new SimpleStringProperty(p.getUsedCount() + " / " + limit);
        });

        tableView.setItems(data);
    }

    // ── Load active promotions ────────────────────────────────────────────────

    private void loadActivePromotions() {
        try {
            List<Promotion> list = svc.getActivePromotions();
            data.setAll(list);
            lblCount.setText("Active promotions: " + list.size());
            clearMsg();
        } catch (Exception e) {
            showMsg("Failed to load promotions: " + e.getMessage(), true);
        }
    }

    @FXML private void handleRefresh() {
        clearLookup();
        loadActivePromotions();
    }

    // ── Code lookup (4.5.1) ───────────────────────────────────────────────────

    /**
     * Validates a code the employee typed, without applying it to any order.
     * Actual application happens in EmployeeOrderController when the order
     * is submitted — this is just a quick "is this code usable?" preview.
     */
    @FXML private void handleLookup() {
        String code = tfLookupCode.getText().trim();
        if (code.isBlank()) {
            setLookupResult("Please enter a promotion code.", false);
            return;
        }

        // Find among the already-loaded ACTIVE list (already filtered by DAO)
        Promotion match = data.stream()
                .filter(p -> p.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);

        if (match == null) {
            // Could be INACTIVE / EXPIRED / UPCOMING — tell employee clearly
            setLookupResult(
                "✘ Code \"" + code + "\" is not active or does not exist.", false);
            return;
        }

        // Build readable summary
        String discStr = "PERCENT".equals(match.getDiscountType())
                ? String.format("%.0f%% off", match.getDiscountValue())
                : String.format("%,.0f ₫ off", match.getDiscountValue());

        String minStr = match.getMinOrderValue().compareTo(java.math.BigDecimal.ZERO) > 0
                ? String.format(" (min order: %,.0f ₫)", match.getMinOrderValue())
                : "";

        String usageStr = match.getUsageLimit() != null
                ? String.format(" — %d/%d uses", match.getUsedCount(), match.getUsageLimit())
                : "";

        String expires = match.getEndDate() != null
                ? " — expires " + match.getEndDate().format(dtf)
                : "";

        setLookupResult(
            "✔ " + match.getCode() + ": " + discStr + minStr + usageStr + expires,
            true);

        // Highlight the matching row in the table
        tableView.getSelectionModel().select(match);
        tableView.scrollTo(match);
    }

    @FXML private void handleClearLookup() {
        clearLookup();
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private void setLookupResult(String msg, boolean success) {
        lblLookupResult.setText(msg);
        lblLookupResult.setStyle(success
                ? "-fx-text-fill:#27ae60; -fx-font-weight:bold;"
                : "-fx-text-fill:#c62828; -fx-font-weight:bold;");
    }

    private void clearLookup() {
        tfLookupCode.clear();
        lblLookupResult.setText("");
        lblLookupResult.setStyle("");
        tableView.getSelectionModel().clearSelection();
    }

    private void showMsg(String m, boolean error) {
        lblMessage.setStyle(error
                ? "-fx-text-fill:#c62828; -fx-font-size:12;"
                : "-fx-text-fill:#27ae60; -fx-font-size:12;");
        lblMessage.setText(m);
    }

    private void clearMsg() {
        lblMessage.setText("");
        lblMessage.setStyle("");
    }
}
