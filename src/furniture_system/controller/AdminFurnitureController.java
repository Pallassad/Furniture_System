package furniture_system.controller;

import furniture_system.model.FurnitureType;
import furniture_system.model.Product;
import furniture_system.model.Supplier;
import furniture_system.service.FurnitureTypeService;
import furniture_system.service.ProductService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Admin Furniture Management Controller.
 * Full CRUD: view, add, update, soft-delete (→INACTIVE), search.
 */
public class AdminFurnitureController implements Initializable {

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<Product> tableView;
    @FXML private TableColumn<Product, Integer> colId;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, String> colType;
    @FXML private TableColumn<Product, String> colSupplier;
    @FXML private TableColumn<Product, BigDecimal> colPrice;
    @FXML private TableColumn<Product, Integer> colWarranty;
    @FXML private TableColumn<Product, Product.Status> colStatus;          // ← ĐÃ SỬA: dùng Product.Status
    @FXML private TableColumn<Product, Void> colActions;

    // ── Search ─────────────────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Label lblCount;
    @FXML private Label lblMessage;

    // ── Form ───────────────────────────────────────────────────────────────
    @FXML private TextField txtName;
    @FXML private ComboBox<FurnitureType> cmbType;
    @FXML private ComboBox<Supplier> cmbSupplier;
    @FXML private TextField txtPrice;
    @FXML private TextArea txtDescription;
    @FXML private Spinner<Integer> spnWarranty;
    @FXML private ComboBox<Product.Status> cmbStatus;

    // ── Buttons ────────────────────────────────────────────────────────────
    @FXML private Button btnAdd;
    @FXML private Button btnUpdate;
    @FXML private Button btnClear;

    // ── Form feedback ──────────────────────────────────────────────────────
    @FXML private Label lblFormMessage;

    // ── State ──────────────────────────────────────────────────────────────
    private final ProductService svcProduct = new ProductService();
    private final FurnitureTypeService svcType = new FurnitureTypeService();

    private final ObservableList<Product> data = FXCollections.observableArrayList();
    private Product selectedProduct = null;

    private static final String BTN_STYLE_EDIT =
            "-fx-background-color:#3949ab;-fx-text-fill:white;-fx-cursor:hand;" +
            "-fx-background-radius:4;-fx-font-size:11;";

    private static final String BTN_STYLE_DEACT =
            "-fx-background-color:#c62828;-fx-text-fill:white;-fx-cursor:hand;" +
            "-fx-background-radius:4;-fx-font-size:11;";

    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initTable();
        initForm();
        loadCombos();
        refreshTable();
        clearForm();
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void initTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("productId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colType.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getTypeName())));
        colSupplier.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getSupplierName())));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colWarranty.setCellValueFactory(new PropertyValueFactory<>("warrantyMonths"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));  // ← giữ nguyên, giờ khớp kiểu

        // Price formatted
        colPrice.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%,.0f ₫", v));
            }
        });

        // Status column – ĐÃ SỬA: nhận trực tiếp Product.Status
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Product.Status status, boolean empty) {
                super.updateItem(status, empty);

                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                // Hiển thị tên trạng thái (tùy thuộc toString() của enum)
                setText(status.toString());

                setStyle(switch (status) {
                    case ACTIVE       -> "-fx-text-fill:#27ae60; -fx-font-weight:bold;";
                    case INACTIVE     -> "-fx-text-fill:#c62828; -fx-font-weight:bold;";
                    case OUT_OF_STOCK -> "-fx-text-fill:#e65100; -fx-font-weight:bold;";
                });
            }
        });

        // Actions column
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("✏ Edit");
            private final Button btnDeact = new Button("🗑 Deactivate");

            {
                btnEdit.setStyle(BTN_STYLE_EDIT);
                btnDeact.setStyle(BTN_STYLE_DEACT);

                btnEdit.setOnAction(e -> {
                    Product p = getTableRow().getItem();
                    if (p != null) populateForm(p);
                });

                btnDeact.setOnAction(e -> {
                    Product p = getTableRow().getItem();
                    if (p != null) confirmDeactivate(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : new HBox(5, btnEdit, btnDeact));
            }
        });

        tableView.setItems(data);

        tableView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, p) -> {
                    if (p != null) populateForm(p);
                });
    }

    // ── Form setup ─────────────────────────────────────────────────────────
    private void initForm() {
        cmbStatus.setItems(FXCollections.observableArrayList(Product.Status.values()));
        cmbStatus.setValue(Product.Status.ACTIVE);

        spnWarranty.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 999, 0));
        spnWarranty.setEditable(true);

        btnUpdate.setDisable(true);
    }

    private void loadCombos() {
        try {
            List<FurnitureType> types = svcType.getAll();
            cmbType.setItems(FXCollections.observableArrayList(types));

            List<Supplier> suppliers = svcProduct.getActiveSuppliers();
            cmbSupplier.setItems(FXCollections.observableArrayList(suppliers));
        } catch (SQLException e) {
            showTableError("Could not load combo data: " + e.getMessage());
        }
    }

    // ── Refresh & Search ───────────────────────────────────────────────────
    private void refreshTable() {
        try {
            data.setAll(svcProduct.getAllProducts());
            updateCount();
        } catch (SQLException e) {
            showTableError("Failed to load products: " + e.getMessage());
        }
    }

    @FXML private void handleSearch() {
        try {
            data.setAll(svcProduct.searchProducts(txtSearch.getText()));
            updateCount();
            clearTableMsg();
        } catch (SQLException e) {
            showTableError("Search error: " + e.getMessage());
        }
    }

    @FXML private void handleClearSearch() {
        txtSearch.clear();
        refreshTable();
        clearTableMsg();
    }

    // ── Add / Update / Deactivate / Clear ──────────────────────────────────
    // (các hàm này giữ nguyên như code cũ của bạn, không thay đổi logic)

    @FXML private void handleAdd(ActionEvent e) {
        Product p = buildFromForm();
        if (p == null) return;
        try {
            svcProduct.addProduct(p);
            showFormSuccess("✔ Product added successfully.");
            refreshTable();
            clearForm();
        } catch (IllegalArgumentException | SQLException ex) {
            showFormError(ex.getMessage());
        }
    }

    @FXML private void handleUpdate(ActionEvent e) {
        if (selectedProduct == null) {
            showFormError("Select a product to update.");
            return;
        }
        Product p = buildFromForm();
        if (p == null) return;
        p.setProductId(selectedProduct.getProductId());
        try {
            svcProduct.updateProduct(p);
            showFormSuccess("✔ Product updated successfully.");
            refreshTable();
            clearForm();
        } catch (IllegalArgumentException | SQLException ex) {
            showFormError(ex.getMessage());
        }
    }

    private void confirmDeactivate(Product p) {
        Alert dlg = new Alert(Alert.AlertType.CONFIRMATION,
                "Set \"" + p.getName() + "\" to INACTIVE?\n\n" +
                        "The product will no longer be visible to customers.",
                ButtonType.YES, ButtonType.NO);
        dlg.setTitle("Confirm Deactivation");
        dlg.setHeaderText(null);
        Optional<ButtonType> res = dlg.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.YES) {
            try {
                svcProduct.deactivateProduct(p.getProductId());
                showTableSuccess("Product \"" + p.getName() + "\" deactivated.");
                refreshTable();
                clearForm();
            } catch (SQLException ex) {
                showTableError("Database error: " + ex.getMessage());
            }
        }
    }

    @FXML private void handleClear() {
        clearForm();
    }

    private void clearForm() {
        selectedProduct = null;
        txtName.clear();
        cmbType.setValue(null);
        cmbSupplier.setValue(null);
        txtPrice.clear();
        txtDescription.clear();
        spnWarranty.getValueFactory().setValue(0);
        cmbStatus.setValue(Product.Status.ACTIVE);
        tableView.getSelectionModel().clearSelection();
        btnUpdate.setDisable(true);
        btnAdd.setDisable(false);
        clearFormMsg();
    }

    private void populateForm(Product p) {
        selectedProduct = p;
        txtName.setText(p.getName());
        cmbType.getItems().stream()
                .filter(t -> t.getTypeId() == p.getTypeId())
                .findFirst().ifPresent(cmbType::setValue);
        cmbSupplier.getItems().stream()
                .filter(s -> s.getSupplierId() == p.getSupplierId())
                .findFirst().ifPresent(cmbSupplier::setValue);
        txtPrice.setText(p.getPrice() != null ? p.getPrice().toPlainString() : "");
        txtDescription.setText(p.getDescription() != null ? p.getDescription() : "");
        spnWarranty.getValueFactory().setValue(p.getWarrantyMonths());
        cmbStatus.setValue(p.getStatus());
        btnUpdate.setDisable(false);
        btnAdd.setDisable(true);
        clearFormMsg();
    }

    private Product buildFromForm() {
        if (cmbType.getValue() == null) { showFormError("Please select a Furniture Type."); return null; }
        if (cmbSupplier.getValue() == null) { showFormError("Please select a Supplier."); return null; }
        if (cmbStatus.getValue() == null) { showFormError("Please select a Status."); return null; }

        BigDecimal price;
        try {
            String priceStr = txtPrice.getText().trim().replace(",", "");
            price = new BigDecimal(priceStr);
        } catch (NumberFormatException ex) {
            showFormError("Price must be a valid number (e.g. 1500000).");
            return null;
        }

        Product p = new Product();
        p.setName(txtName.getText().trim());
        p.setTypeId(cmbType.getValue().getTypeId());
        p.setSupplierId(cmbSupplier.getValue().getSupplierId());
        p.setPrice(price);
        String desc = txtDescription.getText().trim();
        p.setDescription(desc.isEmpty() ? null : desc);
        p.setWarrantyMonths(spnWarranty.getValue());
        p.setStatus(cmbStatus.getValue());
        return p;
    }

    // ── UI helpers ──────────────────────────────────────────────────────────
    private void updateCount() {
        lblCount.setText("Total: " + data.size() + " product(s)");
    }

    private static String nvl(String s) {
        return s != null ? s : "—";
    }

    private void showTableError(String m) {
        lblMessage.setStyle("-fx-text-fill:#c62828; -fx-font-size:12;");
        lblMessage.setText("✘ " + m);
    }

    private void showTableSuccess(String m) {
        lblMessage.setStyle("-fx-text-fill:#27ae60; -fx-font-size:12;");
        lblMessage.setText(m);
    }

    private void clearTableMsg() {
        lblMessage.setText("");
        lblMessage.setStyle("");
    }

    private void showFormError(String m) {
        lblFormMessage.setStyle("-fx-text-fill:#c62828; -fx-background-color:#ffebee;" +
                "-fx-padding:5 8; -fx-background-radius:4; -fx-font-size:12;");
        lblFormMessage.setText("✘ " + m);
    }

    private void showFormSuccess(String m) {
        lblFormMessage.setStyle("-fx-text-fill:#27ae60; -fx-background-color:#e8f5e9;" +
                "-fx-padding:5 8; -fx-background-radius:4; -fx-font-size:12;");
        lblFormMessage.setText(m);
    }

    private void clearFormMsg() {
        lblFormMessage.setText("");
        lblFormMessage.setStyle("");
    }
}