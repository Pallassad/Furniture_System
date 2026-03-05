package furniture_system.controller;

import furniture_system.model.Product;
import furniture_system.service.ProductService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

/**
 * Employee Furniture Controller.
 * READ-ONLY – shows only ACTIVE products.
 * Supports search by name / type and optional price range.
 */
public class EmployeeFurnitureController implements Initializable {

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<Product> tableView;
    @FXML private TableColumn<Product, Integer> colId;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, String> colType;
    @FXML private TableColumn<Product, String> colSupplier;
    @FXML private TableColumn<Product, BigDecimal> colPrice;
    @FXML private TableColumn<Product, Integer> colWarranty;
    @FXML private TableColumn<Product, Product.Status> colStatus;  // ← SỬA: dùng Product.Status thay vì String

    // ── Search ─────────────────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private TextField txtMinPrice;
    @FXML private TextField txtMaxPrice;
    @FXML private Label lblCount;
    @FXML private Label lblMessage;

    // ── Detail panel ───────────────────────────────────────────────────────
    @FXML private Label lblDetailName;
    @FXML private Label lblDetailType;
    @FXML private Label lblDetailSupplier;
    @FXML private Label lblDetailPrice;
    @FXML private Label lblDetailWarranty;
    @FXML private Label lblDetailStatus;
    @FXML private TextArea txtDetailDescription;

    private final ProductService svc = new ProductService();
    private final ObservableList<Product> data = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initTable();
        loadActive();
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void initTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("productId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colType.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getTypeName())));
        colSupplier.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getSupplierName())));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colWarranty.setCellValueFactory(new PropertyValueFactory<>("warrantyMonths"));

        // Price formatted
        colPrice.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%,.0f ₫", v));
            }
        });

        // Status column – ĐÃ SỬA: nhận trực tiếp Product.Status
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Product.Status status, boolean empty) {
                super.updateItem(status, empty);

                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                // Vì employee chỉ thấy ACTIVE → luôn hiển thị "Active"
                setText("Active");
                setStyle("-fx-text-fill:#27ae60; -fx-font-weight:bold;");
            }
        });

        tableView.setItems(data);

        // Khi chọn dòng → hiển thị chi tiết
        tableView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, p) -> {
                    if (p != null) showDetail(p);
                    else clearDetail();
                });
    }

    // ── Data ───────────────────────────────────────────────────────────────
    private void loadActive() {
        try {
            data.setAll(svc.getActiveProducts());
            updateCount();
        } catch (SQLException e) {
            showError("Failed to load active products: " + e.getMessage());
        }
    }

    // ── Search ─────────────────────────────────────────────────────────────
    @FXML
    private void handleSearch(ActionEvent e) {
        BigDecimal min = parsePrice(txtMinPrice, "Min price");
        if (min == null && !txtMinPrice.getText().isBlank()) return;

        BigDecimal max = parsePrice(txtMaxPrice, "Max price");
        if (max == null && !txtMaxPrice.getText().isBlank()) return;

        try {
            data.setAll(svc.searchActiveProducts(txtSearch.getText(), min, max));
            updateCount();
            clearMsg();
        } catch (SQLException ex) {
            showError("Search error: " + ex.getMessage());
        }
    }

    @FXML
    private void handleClearSearch(ActionEvent e) {
        txtSearch.clear();
        txtMinPrice.clear();
        txtMaxPrice.clear();
        clearDetail();
        clearMsg();
        loadActive();
    }

    // ── Detail panel ───────────────────────────────────────────────────────
    private void showDetail(Product p) {
        lblDetailName.setText(p.getName());
        lblDetailType.setText(nvl(p.getTypeName()));
        lblDetailSupplier.setText(nvl(p.getSupplierName()));
        lblDetailPrice.setText(p.getPrice() != null
                ? String.format("%,.0f ₫", p.getPrice()) : "—");
        lblDetailWarranty.setText(p.getWarrantyMonths() + " month(s)");
        lblDetailStatus.setText("Active");
        txtDetailDescription.setText(
                p.getDescription() != null && !p.getDescription().isBlank()
                        ? p.getDescription() : "(No description available)");
    }

    private void clearDetail() {
        lblDetailName.setText("—");
        lblDetailType.setText("—");
        lblDetailSupplier.setText("—");
        lblDetailPrice.setText("—");
        lblDetailWarranty.setText("—");
        lblDetailStatus.setText("—");
        txtDetailDescription.setText("");
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private BigDecimal parsePrice(TextField field, String label) {
        String text = field.getText().trim();
        if (text.isBlank()) return null;
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException ex) {
            showError(label + " must be a valid number.");
            return null;
        }
    }

    private void updateCount() {
        lblCount.setText("Showing " + data.size() + " active product(s)");
    }

    private static String nvl(String s) {
        return s != null ? s : "—";
    }

    private void showError(String msg) {
        lblMessage.setStyle("-fx-text-fill:#c62828; -fx-font-size:12;");
        lblMessage.setText("✘ " + msg);
    }

    private void clearMsg() {
        lblMessage.setText("");
        lblMessage.setStyle("");
    }
}