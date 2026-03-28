package furniture_system.controller;

import furniture_system.model.FurnitureType;
import furniture_system.model.Product;
import furniture_system.service.FurnitureTypeService;
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
 * Employee – Furniture Types screen.
 * READ-ONLY. Browse active products filtered by furniture type.
 * Double-click or "View Details" opens a read-only Modal Dialog.
 */
public class EmployeeFurnitureTypeController {

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<Product>               productTable;
    @FXML private TableColumn<Product, Integer>    colProductId;
    @FXML private TableColumn<Product, String>     colProductName;
    @FXML private TableColumn<Product, String>     colProductType;
    @FXML private TableColumn<Product, BigDecimal> colProductPrice;
    @FXML private TableColumn<Product, String>     colProductStatus;

    // ── Toolbar & Filter ───────────────────────────────────────────────────
    @FXML private ComboBox<FurnitureType> cmbTypeFilter;
    @FXML private TextField               txtProductSearch;
    @FXML private Button                  btnViewDetail;
    @FXML private Button                  btnRefresh;
    @FXML private Label                   statusBarLabel;

    private final FurnitureTypeService             typeService    = new FurnitureTypeService();
    private final ProductService                   productService = new ProductService();
    private final ObservableList<Product>          data           = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupProductTable();
        loadTypeFilter();
        loadAllProducts();

        productTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> btnViewDetail.setDisable(sel == null));
        btnViewDetail.setDisable(true);

        // Double-click row → detail dialog
        productTable.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openDetailDialog(row.getItem());
            });
            return row;
        });
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void setupProductTable() {
        colProductId.setCellValueFactory(c ->
                new javafx.beans.property.SimpleIntegerProperty(c.getValue().getProductId()).asObject());
        colProductName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getName()));
        colProductType.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTypeName() != null ? c.getValue().getTypeName() : "—"));
        colProductPrice.setCellValueFactory(c ->
                new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPrice()));
        colProductStatus.setCellValueFactory(c ->
                new SimpleStringProperty("Active"));

        colProductPrice.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? "—" : String.format("%,.0f ₫", v));
            }
        });

        colProductStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle("-fx-text-fill:#27ae60;-fx-font-weight:bold;");
            }
        });

        productTable.setItems(data);
    }

    // ── Load type filter combo ─────────────────────────────────────────────
    private void loadTypeFilter() {
        try {
            List<FurnitureType> types = typeService.getActive();

            FurnitureType allOption = new FurnitureType();
            allOption.setTypeId(0);
            allOption.setTypeName("— All Categories —");
            types.add(0, allOption);

            cmbTypeFilter.setItems(FXCollections.observableArrayList(types));
            cmbTypeFilter.setValue(allOption);
            cmbTypeFilter.setOnAction(e -> applyFilter());
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Error", "Could not load furniture types: " + e.getMessage());
        }
    }

    // ── Filter logic ───────────────────────────────────────────────────────
    private void applyFilter() {
        FurnitureType sel   = cmbTypeFilter.getValue();
        String        kw    = txtProductSearch.getText().trim();
        try {
            List<Product> list;
            if (sel == null || sel.getTypeId() == 0) {
                list = productService.searchActiveProducts(kw, null, null);
            } else {
                list = productService.searchActiveProducts(kw, null, null)
                        .stream().filter(p -> p.getTypeId() == sel.getTypeId()).toList();
            }
            data.setAll(list);
            setStatus("Showing " + list.size() + " product(s)"
                    + (sel != null && sel.getTypeId() != 0 ? " in [" + sel.getTypeName() + "]." : "."));
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Filter Error", e.getMessage());
        }
    }

    @FXML public void handleSearch()  { applyFilter(); }

    @FXML public void handleRefresh() {
        txtProductSearch.clear();
        if (cmbTypeFilter.getItems() != null && !cmbTypeFilter.getItems().isEmpty())
            cmbTypeFilter.setValue(cmbTypeFilter.getItems().get(0));
        loadAllProducts();
    }

    private void loadAllProducts() {
        try {
            List<Product> list = productService.getActiveProducts();
            data.setAll(list);
            setStatus("Showing " + list.size() + " active product(s).");
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Error", "Failed to load products: " + e.getMessage());
        }
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

        Label lblName = new Label(p.getName());
        lblName.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1a237e;");
        lblName.setWrapText(true);
        grid.add(lblName, 0, row, 2, 1); row++;
        grid.add(new Separator(), 0, row, 2, 1); row++;

        grid.add(sec("-- Product Info"), 0, row, 2, 1); row++;
        grid.add(fl("Product ID"),  0, row); grid.add(val("" + p.getProductId()),               1, row++);
        grid.add(fl("Category"),    0, row); grid.add(val(nvl(p.getTypeName())),                 1, row++);
        grid.add(fl("Supplier"),    0, row); grid.add(val(nvl(p.getSupplierName())),             1, row++);
        grid.add(new Separator(),   0, row, 2, 1); row++;

        grid.add(sec("-- Pricing & Warranty"), 0, row, 2, 1); row++;
        Label lblPrice = new Label(p.getPrice() != null ? String.format("%,.0f ₫", p.getPrice()) : "—");
        lblPrice.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#27ae60;");
        grid.add(fl("Price"),       0, row); grid.add(lblPrice,                                  1, row++);
        grid.add(fl("Warranty"),    0, row); grid.add(val(p.getWarrantyMonths() + " month(s)"),  1, row++);
        grid.add(new Separator(),   0, row, 2, 1); row++;

        Label lblStatus = new Label("● Active");
        lblStatus.setStyle("-fx-text-fill:#27ae60;-fx-font-weight:bold;-fx-font-size:12px;");
        grid.add(sec("-- Status"),  0, row, 2, 1); row++;
        grid.add(fl("Status"),      0, row); grid.add(lblStatus,                                 1, row++);
        grid.add(new Separator(),   0, row, 2, 1); row++;

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
        dlg.setResizable(false); dlg.showAndWait();
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private Label fl(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t) { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
    private Label val(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:12px;"); l.setWrapText(true); return l; }
    private void setStatus(String msg) { setStatus(msg, false); }
    private void setStatus(String msg, boolean isError) {
        if (statusBarLabel == null) return;
        statusBarLabel.setText(msg);
        if (isError) {
            statusBarLabel.setStyle("-fx-text-fill:#c0392b;-fx-font-weight:bold;");
        } else if (msg.startsWith("✔") || msg.contains("added") || msg.contains("updated")
                || msg.contains("deleted") || msg.contains("created") || msg.contains("saved")
                || msg.contains("recorded") || msg.contains("linked") || msg.contains("success")) {
            statusBarLabel.setStyle("-fx-text-fill:#1e7e4a;-fx-font-weight:bold;");
        } else {
            statusBarLabel.setStyle("-fx-text-fill:#6878aa;-fx-font-weight:normal;");
        }
    }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
    private static String nvl(String s) { return s != null ? s : "—"; }
}