package furniture_system.controller;

import furniture_system.model.Stock;
import furniture_system.service.StockService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

/**
 * 4.8 – Employee read-only view of stock levels.
 * Employees cannot modify stock; they can only view and check availability.
 */
public class EmployeeStockController {

    // ── Stock Table ────────────────────────────────────────────────────────
    @FXML private TableView<Stock>            tblStock;
    @FXML private TableColumn<Stock, String>  colProduct;
    @FXML private TableColumn<Stock, Integer> colQty;
    @FXML private TableColumn<Stock, Integer> colReorder;
    @FXML private TableColumn<Stock, String>  colUpdated;
    @FXML private TableColumn<Stock, String>  colAlert;

    // ── Availability Check ─────────────────────────────────────────────────
    @FXML private ComboBox<furniture_system.model.Product> cbCheckProduct;
    @FXML private TextField txRequiredQty;
    @FXML private Label     lblAvailResult;

    // ── Status ──────────────────────────────────────────────────────────────
    @FXML private Label lblStatus;

    private final StockService service = new StockService();

    @FXML
    public void initialize() {
        setupTable();
        loadProductCombo();
        loadStock();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Setup
    // ══════════════════════════════════════════════════════════════════════

    private void setupTable() {
        colProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colReorder.setCellValueFactory(new PropertyValueFactory<>("reorderLevel"));
        colUpdated.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getLastUpdated() != null
                ? c.getValue().getLastUpdated().toString().replace("T", " ")
                : "—"));
        colAlert.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().isBelowReorder() ? "⚠ Low Stock" : "✓ OK"));

        // Highlight rows below reorder level (4.8.1 requirement)
        tblStock.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Stock item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                setStyle(item.isBelowReorder()
                    ? "-fx-background-color:#fff3e0;" : "");
            }
        });
    }

    private void loadProductCombo() {
        try {
            furniture_system.service.ProductService ps = new furniture_system.service.ProductService();
            cbCheckProduct.setItems(FXCollections.observableArrayList(ps.getAllProducts()));
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Load
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void handleRefresh() { loadStock(); }

    private void loadStock() {
        try {
            List<Stock> list = service.getAllStock();
            tblStock.setItems(FXCollections.observableArrayList(list));
            long low = list.stream().filter(Stock::isBelowReorder).count();
            setStatus("Showing " + list.size() + " products" +
                (low > 0 ? " — ⚠ " + low + " below reorder level." : "."), low > 0);
        } catch (Exception e) {
            setStatus("Error loading stock: " + e.getMessage(), true);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  4.8.2 – Availability Check
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void handleCheckAvailability() {
        lblAvailResult.setText("");
        furniture_system.model.Product p = cbCheckProduct.getValue();
        if (p == null) { lblAvailResult.setText("Please select a product."); return; }
        String qtyText = txRequiredQty.getText().trim();
        if (qtyText.isEmpty()) { lblAvailResult.setText("Please enter required quantity."); return; }

        try {
            int required = Integer.parseInt(qtyText);
            if (required <= 0) { lblAvailResult.setText("Required quantity must be > 0."); return; }

            Stock stock = service.getStock(p.getProductId());
            if (stock == null) {
                lblAvailResult.setStyle("-fx-text-fill:#c62828;");
                lblAvailResult.setText("No stock record found for this product.");
                return;
            }

            if (stock.getQuantity() >= required) {
                lblAvailResult.setStyle("-fx-text-fill:#2e7d32; -fx-font-weight:bold;");
                lblAvailResult.setText("✓ Available — Current stock: " + stock.getQuantity()
                    + "  (Requested: " + required + ")");
            } else {
                lblAvailResult.setStyle("-fx-text-fill:#c62828; -fx-font-weight:bold;");
                lblAvailResult.setText("✗ Insufficient — Current stock: " + stock.getQuantity()
                    + "  (Requested: " + required + ", Short by: "
                    + (required - stock.getQuantity()) + ")");
            }
        } catch (NumberFormatException ex) {
            lblAvailResult.setText("Quantity must be a valid number.");
        } catch (Exception ex) {
            lblAvailResult.setText("Error: " + ex.getMessage());
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private void setStatus(String msg, boolean isError) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle(isError
            ? "-fx-text-fill:#c62828; -fx-font-size:12px;"
            : "-fx-text-fill:#2e7d32; -fx-font-size:12px;");
    }
}
