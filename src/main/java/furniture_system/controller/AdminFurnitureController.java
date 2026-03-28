package furniture_system.controller;

import furniture_system.model.FurnitureType;
import furniture_system.model.Product;
import furniture_system.model.Supplier;
import furniture_system.service.FurnitureTypeService;
import furniture_system.service.ProductService;
import furniture_system.utils.NotificationUtil;
import furniture_system.utils.SearchableComboBox;
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

public class AdminFurnitureController {

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<Product>               productTable;
    @FXML private TableColumn<Product, Integer>    colId;
    @FXML private TableColumn<Product, String>     colName;
    @FXML private TableColumn<Product, String>     colType;
    @FXML private TableColumn<Product, String>     colSupplier;
    @FXML private TableColumn<Product, BigDecimal> colPrice;
    @FXML private TableColumn<Product, Integer>    colWarranty;
    @FXML private TableColumn<Product, String>     colStatus;

    // ── Search & Status ────────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Label     statusBarLabel;

    // ── Toolbar Buttons ────────────────────────────────────────────────────
    @FXML private Button btnAdd;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnRefresh;

    // ── Services ───────────────────────────────────────────────────────────
    private final ProductService       svcProduct = new ProductService();
    private final FurnitureTypeService svcType    = new FurnitureTypeService();

    private final ObservableList<Product> data = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupColumns();
        loadProducts();

        productTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    boolean has = sel != null;
                    btnEdit.setDisable(!has);
                    btnDelete.setDisable(!has);
                });
        btnEdit.setDisable(true);
        btnDelete.setDisable(true);
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
                new SimpleStringProperty(c.getValue().getStatus() != null ? c.getValue().getStatus().name() : "—"));

        // Price format
        colPrice.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%,.0f ₫", v));
            }
        });

        // Status color
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                setStyle(switch (status) {
                    case "ACTIVE"       -> "-fx-text-fill:#27ae60;-fx-font-weight:bold;";
                    case "INACTIVE"     -> "-fx-text-fill:#c62828;-fx-font-weight:bold;";
                    case "OUT_OF_STOCK" -> "-fx-text-fill:#e65100;-fx-font-weight:bold;";
                    default             -> "";
                });
            }
        });

        // Row color for INACTIVE
        productTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                if (item.getStatus() == Product.Status.INACTIVE)
                    setStyle("-fx-background-color:#e8e8e8;");
                else if (item.getStatus() == Product.Status.OUT_OF_STOCK)
                    setStyle("-fx-background-color:#fff3e0;");
                else setStyle("");
            }
        });

        productTable.setItems(data);
    }

    // ── Load ───────────────────────────────────────────────────────────────
    private void loadProducts() {
        try {
            List<Product> list = svcProduct.getAllProducts();
            data.setAll(list);
            setStatus("Loaded " + list.size() + " product(s).");
        } catch (Exception e) {
alert(Alert.AlertType.ERROR, "Error", "Failed to load products: " + e.getMessage());
        }
    }

    @FXML public void handleRefresh() {
        txtSearch.clear();
        loadProducts();
    }

    @FXML public void handleSearch() {
        try {
            List<Product> list = svcProduct.searchProducts(txtSearch.getText().trim());
            data.setAll(list);
            setStatus("Found " + list.size() + " product(s).");
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Search Error", e.getMessage());
        }
    }

    // ==================== ADD ====================
    @FXML
    public void handleAdd() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Add New Product");

        TextField        tfName     = new TextField();
        TextField        tfPrice    = new TextField();
        TextArea         taDesc     = new TextArea();
        Spinner<Integer> spnWarranty = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 999, 0));
        ComboBox<FurnitureType> cbType     = new ComboBox<>();
        ComboBox<Supplier>     cbSupplier  = new ComboBox<>();
        ComboBox<Product.Status> cbStatus  = new ComboBox<>(FXCollections.observableArrayList(Product.Status.values()));
        spnWarranty.setEditable(true);
        spnWarranty.setMaxWidth(Double.MAX_VALUE);
        spnWarranty.getEditor().setTextFormatter(new TextFormatter<>(change ->
        change.getControlNewText().matches("\\d{0,3}") ? change : null));
        cbStatus.setMaxWidth(Double.MAX_VALUE);
        cbStatus.getSelectionModel().select(Product.Status.ACTIVE);
        taDesc.setPrefRowCount(4); taDesc.setWrapText(true);

        // Load data and wrap with search
        List<FurnitureType> allTypes;
        List<Supplier>      allSuppliers;
        try {
            allTypes     = svcType.getActive();
            allSuppliers = svcProduct.getActiveSuppliers();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Error", "Could not load data: " + e.getMessage()); return;
        }
        VBox vType     = wrapTypeCombo(cbType, allTypes);
        VBox vSupplier = wrapSupplierCombo(cbSupplier, allSuppliers);

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(fl("Product Name *"),      0, row); grid.add(tfName,      1, row++);
        grid.add(new Label(""),             0, row); grid.add(hint("Required. 2–150 characters."), 1, row++);
        grid.add(new Separator(),           0, row, 2, 1); row++;
        grid.add(sec("-- Category & Supplier"), 0, row, 2, 1); row++;
        grid.add(fl("Furniture Type *"),    0, row); grid.add(vType,      1, row++);
        grid.add(fl("Supplier *"),          0, row); grid.add(vSupplier,  1, row++);
grid.add(new Separator(),           0, row, 2, 1); row++;
        grid.add(sec("-- Pricing & Warranty"), 0, row, 2, 1); row++;
        grid.add(fl("Price (VND) *"),       0, row); grid.add(tfPrice,     1, row++);
        grid.add(new Label(""),             0, row); grid.add(hint("Numeric only, e.g. 1500000"), 1, row++);
        grid.add(fl("Warranty (months)"),   0, row); grid.add(spnWarranty, 1, row++);
        grid.add(new Separator(),           0, row, 2, 1); row++;
        grid.add(sec("-- Status & Description"), 0, row, 2, 1); row++;
        grid.add(fl("Status *"),            0, row); grid.add(cbStatus,    1, row++);
        grid.add(fl("Description"),         0, row); grid.add(taDesc,      1, row++);
        grid.add(new Label(""),             0, row); grid.add(hint("Optional — materials, dimensions, etc."), 1, row++);
        grid.add(new Separator(),           0, row, 2, 1); row++;
        grid.add(lblErr,                    0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Add Product");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                Product p = buildProduct(tfName, tfPrice, taDesc, spnWarranty, cbType, cbSupplier, cbStatus, lblErr);
                if (p == null) return;
                svcProduct.addProduct(p);
                setStatus("Product [" + p.getName() + "] added.");
                NotificationUtil.success(productTable, "Product added: " + p.getName());
                loadProducts();
                dlg.close();
            } catch (Exception ex) { lblErr.setText(ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 500, 680)); dlg.showAndWait();
    }

    // ==================== EDIT ====================
    @FXML
    public void handleEdit() {
        Product sel = productTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Product - " + sel.getName());

        TextField        tfName      = new TextField(sel.getName());
        TextField        tfPrice     = new TextField(sel.getPrice() != null ? sel.getPrice().toPlainString() : "");
        TextArea         taDesc      = new TextArea(sel.getDescription() != null ? sel.getDescription() : "");
        Spinner<Integer> spnWarranty = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 999, sel.getWarrantyMonths()));
        ComboBox<FurnitureType>  cbType     = new ComboBox<>();
ComboBox<Supplier>       cbSupplier = new ComboBox<>();
        ComboBox<Product.Status> cbStatus   = new ComboBox<>(FXCollections.observableArrayList(Product.Status.values()));

        spnWarranty.setEditable(true); spnWarranty.setMaxWidth(Double.MAX_VALUE);
        spnWarranty.getEditor().setTextFormatter(new TextFormatter<>(change ->
        change.getControlNewText().matches("\\d{0,3}") ? change : null));
        cbStatus.setMaxWidth(Double.MAX_VALUE); cbStatus.setValue(sel.getStatus());
        taDesc.setPrefRowCount(4); taDesc.setWrapText(true);

        loadCombosForEdit(cbType, cbSupplier, sel);
        // Pre-select current values
        cbType.getItems().stream().filter(t -> t.getTypeId() == sel.getTypeId()).findFirst().ifPresent(cbType::setValue);
        cbSupplier.getItems().stream().filter(s -> s.getSupplierId() == sel.getSupplierId()).findFirst().ifPresent(cbSupplier::setValue);

        // Wrap with search (items already loaded by loadCombosForEdit)
        VBox vType     = wrapTypeCombo(cbType, cbType.getItems());
        VBox vSupplier = wrapSupplierCombo(cbSupplier, cbSupplier.getItems());

        Label lblInfo = new Label("Product ID: " + sel.getProductId());
        lblInfo.setStyle("-fx-text-fill:#555;-fx-font-size:12px;");
        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblInfo,                   0, row, 2, 1); row++;
        grid.add(new Separator(),           0, row, 2, 1); row++;
        grid.add(fl("Product Name *"),      0, row); grid.add(tfName,      1, row++);
        grid.add(new Separator(),           0, row, 2, 1); row++;
        grid.add(sec("-- Category & Supplier"), 0, row, 2, 1); row++;
        grid.add(fl("Furniture Type *"),    0, row); grid.add(vType,      1, row++);
        grid.add(fl("Supplier *"),          0, row); grid.add(vSupplier,  1, row++);
        grid.add(new Separator(),           0, row, 2, 1); row++;
        grid.add(sec("-- Pricing & Warranty"), 0, row, 2, 1); row++;
        grid.add(fl("Price (VND) *"),       0, row); grid.add(tfPrice,     1, row++);
        grid.add(fl("Warranty (months)"),   0, row); grid.add(spnWarranty, 1, row++);
        grid.add(new Separator(),           0, row, 2, 1); row++;
        grid.add(sec("-- Status & Description"), 0, row, 2, 1); row++;
        grid.add(fl("Status *"),            0, row); grid.add(cbStatus,    1, row++);
        grid.add(fl("Description"),         0, row); grid.add(taDesc,      1, row++);
        grid.add(new Separator(),           0, row, 2, 1); row++;
        grid.add(lblErr,                    0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Save Changes");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                Product p = buildProduct(tfName, tfPrice, taDesc, spnWarranty, cbType, cbSupplier, cbStatus, lblErr);
                if (p == null) return;
                p.setProductId(sel.getProductId());
                svcProduct.updateProduct(p);
                setStatus("Product [" + p.getName() + "] updated.");
                NotificationUtil.success(productTable, "Product updated: " + p.getName());
                loadProducts();
                dlg.close();
            } catch (Exception ex) { lblErr.setText(ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 500, 680)); dlg.showAndWait();
    }

    // ==================== HARD DELETE ====================
    @FXML
    public void handleDelete() {
        Product sel = productTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete product \"" + sel.getName() + "\"?\n\n" +
                "⚠ This action cannot be undone.\n" +
                "Requirement: product must not be in any order and have no warranty tickets.\n" +
                "Stock data and stock history will be deleted accordingly.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm product deletion"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                svcProduct.deleteProduct(sel.getProductId());
                setStatus("Permanently deleted product [" + sel.getName() + "].");
            NotificationUtil.warning(productTable, "Deleted: " + sel.getName());
                loadProducts();
            } catch (IllegalStateException ex) {
                alert(Alert.AlertType.ERROR, "Cannot delete", ex.getMessage());
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Delete error", ex.getMessage());
            }
        });
    }

    // ── Shared form builder ────────────────────────────────────────────────
    private Product buildProduct(TextField tfName, TextField tfPrice, TextArea taDesc,
                                  Spinner<Integer> spnWarranty,
                                  ComboBox<FurnitureType> cbType,
                                  ComboBox<Supplier> cbSupplier,
                                  ComboBox<Product.Status> cbStatus,
                                  Label lblErr) {
        String name = tfName.getText().trim();
if (name.isEmpty())            { lblErr.setText("Product name is required."); return null; }
        if (cbType.getValue() == null) { lblErr.setText("Please select a Furniture Type."); return null; }
        if (cbSupplier.getValue() == null) { lblErr.setText("Please select a Supplier."); return null; }
        if (cbStatus.getValue() == null)   { lblErr.setText("Please select a Status."); return null; }

        BigDecimal price;
        try {
            price = new BigDecimal(tfPrice.getText().trim().replace(",", ""));
            if (price.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            lblErr.setText("Price must be a valid positive number (e.g. 1500000).");
            return null;
        }

        Product p = new Product();
        p.setName(name);
        p.setTypeId(cbType.getValue().getTypeId());
        p.setSupplierId(cbSupplier.getValue().getSupplierId());
        p.setPrice(price);
        p.setWarrantyMonths(spnWarranty.getValue());
        p.setStatus(cbStatus.getValue());
        String desc = taDesc.getText().trim();
        p.setDescription(desc.isEmpty() ? null : desc);
        return p;
    }

    /** For ADD dialog — only ACTIVE types and active suppliers. */
    private void loadCombos(ComboBox<FurnitureType> cbType, ComboBox<Supplier> cbSupplier) {
        try {
            List<FurnitureType> types     = svcType.getActive();
            List<Supplier>      suppliers = svcProduct.getActiveSuppliers();
            cbType.setItems(FXCollections.observableArrayList(types));
            cbSupplier.setItems(FXCollections.observableArrayList(suppliers));
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Error", "Could not load combo data: " + e.getMessage());
        }
    }

    /** Wraps the FurnitureType ComboBox with a search field. */
    private VBox wrapTypeCombo(ComboBox<FurnitureType> cb, List<FurnitureType> list) {
        return SearchableComboBox.wrap(cb, list,
                t -> t.getTypeId() + ". " + t.getTypeName());
    }

    /** Wraps the Supplier ComboBox with a search field. */
    private VBox wrapSupplierCombo(ComboBox<Supplier> cb, List<Supplier> list) {
        return SearchableComboBox.wrap(cb, list,
                s -> s.getSupplierId() + ". " + s.getName()
                     + (s.getPhone() != null ? "  (" + s.getPhone() + ")" : ""));
    }

    /**
     * For EDIT dialog — loads ACTIVE types, but also includes the product's
     * current type even if it is INACTIVE (so the existing value is preserved
     * and shown rather than appearing blank).
     */
    private void loadCombosForEdit(ComboBox<FurnitureType> cbType,
                                   ComboBox<Supplier> cbSupplier,
                                   Product sel) {
        try {
// ── Furniture Types ──────────────────────────────────────────────
            List<FurnitureType> activeTypes = new java.util.ArrayList<>(svcType.getActive());
            boolean currentTypePresent = activeTypes.stream()
                    .anyMatch(t -> t.getTypeId() == sel.getTypeId());
            if (!currentTypePresent) {
                svcType.getAll().stream()
                        .filter(t -> t.getTypeId() == sel.getTypeId())
                        .findFirst()
                        .ifPresent(t -> {
                            FurnitureType copy = new FurnitureType(
                                    t.getTypeName() + " [INACTIVE]", t.getDescription(), t.getStatus());
                            copy.setTypeId(t.getTypeId());
                            activeTypes.add(0, copy);
                        });
            }
            cbType.setItems(FXCollections.observableArrayList(activeTypes));

            // ── Suppliers ────────────────────────────────────────────────────
            List<Supplier> activeSuppliers = new java.util.ArrayList<>(svcProduct.getActiveSuppliers());
            boolean currentSupplierPresent = activeSuppliers.stream()
                    .anyMatch(s -> s.getSupplierId() == sel.getSupplierId());
            if (!currentSupplierPresent) {
                svcProduct.getAllSuppliers().stream()
                        .filter(s -> s.getSupplierId() == sel.getSupplierId())
                        .findFirst()
                        .ifPresent(s -> activeSuppliers.add(0, s));
            }
            cbSupplier.setItems(FXCollections.observableArrayList(activeSuppliers));
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Error", "Could not load combo data: " + e.getMessage());
        }
    }

    // ── UI helpers (same pattern as AuthController) ────────────────────────
    private GridPane buildGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(24));
        grid.getColumnConstraints().addAll(new ColumnConstraints(155), new ColumnConstraints(280));
        return grid;
    }

    private Label fl(String t)   { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
    private Label hint(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:10px;-fx-text-fill:#888;"); return l; }
    private Button primaryBtn(String t) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
        return b;
    }
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
