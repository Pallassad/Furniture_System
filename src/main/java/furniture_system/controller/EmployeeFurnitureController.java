package furniture_system.controller;

import furniture_system.model.Product;
import furniture_system.service.ProductService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Employee Furniture Controller.
 * READ-ONLY – shows only ACTIVE products.
 * Supports search by name / type and optional price range.
 * Click row or "View Details" to open a read-only Modal Dialog.
 */
public class EmployeeFurnitureController {

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<Product>               productTable;
    @FXML private TableColumn<Product, Integer>    colId;
    @FXML private TableColumn<Product, String>     colName;
    @FXML private TableColumn<Product, String>     colType;
    @FXML private TableColumn<Product, String>     colSupplier;
    @FXML private TableColumn<Product, BigDecimal> colPrice;
    @FXML private TableColumn<Product, Integer>    colWarranty;
    @FXML private TableColumn<Product, String>     colStatus;

    // ── Search & Toolbar ───────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private TextField txtMinPrice;
    @FXML private TextField txtMaxPrice;
    @FXML private Button    btnViewDetail;
    @FXML private Button    btnRefresh;
    @FXML private Label     statusBarLabel;

    private final ProductService             svc  = new ProductService();
    private final ObservableList<Product>    data = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupColumns();
        loadActive();

        productTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> btnViewDetail.setDisable(sel == null));
        btnViewDetail.setDisable(true);

        // Double-click row → open detail
        productTable.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openDetailDialog(row.getItem());
            });
            return row;
        });
    }

    // ── Column setup ───────────────────────────────────────────────────────
    private void setupColumns() {
        colId.setCellValueFactory(c ->
                new javafx.beans.property.SimpleIntegerProperty(c.getValue().getProductId()).asObject());
        colName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getName()));
        colType.setCellValueFactory(c ->
                new SimpleStringProperty(nvl(c.getValue().getTypeName())));
        colSupplier.setCellValueFactory(c ->
                new SimpleStringProperty(nvl(c.getValue().getSupplierName())));
        colPrice.setCellValueFactory(c ->
                new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPrice()));
        colWarranty.setCellValueFactory(c ->
                new javafx.beans.property.SimpleIntegerProperty(c.getValue().getWarrantyMonths()).asObject());
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty("Active"));

        // Price format
        colPrice.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%,.0f ₫", v));
            }
        });

        // Status — always Active (green) for employee view
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle("-fx-text-fill:#27ae60;-fx-font-weight:bold;");
            }
        });

        productTable.setItems(data);
    }

    // ── Data ───────────────────────────────────────────────────────────────
    private void loadActive() {
        try {
            List<Product> list = svc.getActiveProducts();
            data.setAll(list);
            setStatus("Showing " + list.size() + " active product(s).");
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Error", "Failed to load products: " + e.getMessage());
        }
    }

    // ── Search ─────────────────────────────────────────────────────────────
    @FXML
    public void handleSearch() {
        BigDecimal min = parsePrice(txtMinPrice, "Min price");
        if (min == null && !txtMinPrice.getText().isBlank()) return;
        BigDecimal max = parsePrice(txtMaxPrice, "Max price");
        if (max == null && !txtMaxPrice.getText().isBlank()) return;
        try {
            List<Product> list = svc.searchActiveProducts(txtSearch.getText(), min, max);
            data.setAll(list);
            setStatus("Found " + list.size() + " product(s).");
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Search Error", ex.getMessage());
        }
    }

    @FXML
    public void handleRefresh() {
        txtSearch.clear(); txtMinPrice.clear(); txtMaxPrice.clear();
        loadActive();
    }

    // ==================== VIEW DETAIL DIALOG ====================
    @FXML
    public void handleViewDetail() {
        Product sel = productTable.getSelectionModel().getSelectedItem();
        if (sel != null) openDetailDialog(sel);
    }

    private void openDetailDialog(Product p) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Product Detail - " + p.getName());

        GridPane grid = new GridPane();
        grid.setHgap(16); grid.setVgap(12); grid.setPadding(new Insets(28));
        grid.getColumnConstraints().addAll(new ColumnConstraints(120), new ColumnConstraints(300));

        int row = 0;

        // Product name header
        Label lblName = new Label(p.getName());
        lblName.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1a237e;");
        lblName.setWrapText(true);
        grid.add(lblName, 0, row, 2, 1); row++;
        grid.add(new Separator(), 0, row, 2, 1); row++;

        grid.add(sec("-- Product Info"), 0, row, 2, 1); row++;
        grid.add(fl("Product ID"),   0, row); grid.add(val("" + p.getProductId()),       1, row++);
        grid.add(fl("Category"),     0, row); grid.add(val(nvl(p.getTypeName())),         1, row++);
        grid.add(fl("Supplier"),     0, row); grid.add(val(nvl(p.getSupplierName())),     1, row++);
        grid.add(new Separator(),    0, row, 2, 1); row++;

        grid.add(sec("-- Pricing & Warranty"), 0, row, 2, 1); row++;
        String priceStr = p.getPrice() != null ? String.format("%,.0f ₫", p.getPrice()) : "—";
        Label lblPrice = new Label(priceStr);
        lblPrice.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#27ae60;");
        grid.add(fl("Price"),        0, row); grid.add(lblPrice,                          1, row++);
        grid.add(fl("Warranty"),     0, row); grid.add(val(p.getWarrantyMonths() + " month(s)"), 1, row++);
        grid.add(new Separator(),    0, row, 2, 1); row++;

        grid.add(sec("-- Status"), 0, row, 2, 1); row++;
        Label lblStatus = new Label("● Active");
        lblStatus.setStyle("-fx-text-fill:#27ae60;-fx-font-weight:bold;-fx-font-size:12px;");
        grid.add(fl("Status"),       0, row); grid.add(lblStatus,                         1, row++);
        grid.add(new Separator(),    0, row, 2, 1); row++;

        grid.add(sec("-- Description"), 0, row, 2, 1); row++;
        TextArea taDesc = new TextArea(
                p.getDescription() != null && !p.getDescription().isBlank()
                ? p.getDescription() : "(No description available)");
        taDesc.setEditable(false); taDesc.setWrapText(true); taDesc.setPrefRowCount(5);
        taDesc.setStyle("-fx-background-color:#f5f6fa;-fx-font-size:12px;");
        grid.add(taDesc, 0, row, 2, 1); row++;
        grid.add(new Separator(), 0, row, 2, 1); row++;

        Button btnClose = new Button("Close");
        btnClose.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;" +
                "-fx-background-radius:6;-fx-padding:8 24;-fx-font-weight:bold;");
        HBox btns = new HBox(btnClose); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnClose.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 480, 560));
        dlg.setResizable(false);
        dlg.showAndWait();
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private BigDecimal parsePrice(TextField field, String label) {
        String text = field.getText().trim();
        if (text.isBlank()) return null;
        try { return new BigDecimal(text.replace(",", "")); }
        catch (NumberFormatException ex) {
            alert(Alert.AlertType.WARNING, "Invalid Input", label + " must be a valid number.");
            return null;
        }
    }

    private Label fl(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t) { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
    private Label val(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:12px;"); l.setWrapText(true); return l; }
    private void setStatus(String msg) { if (statusBarLabel != null) statusBarLabel.setText(msg); }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
    private static String nvl(String s) { return s != null ? s : "—"; }
}