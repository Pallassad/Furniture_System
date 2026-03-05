package furniture_system.controller;

import furniture_system.model.Supplier;
import furniture_system.model.SupplierProduct;
import furniture_system.service.SupplierService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class SupplierController {

    // ── Supplier Table ─────────────────────────────────────────────────────
    @FXML private TableView<Supplier>              tblSuppliers;
    @FXML private TableColumn<Supplier, Integer>   colSuppId;
    @FXML private TableColumn<Supplier, String>    colSuppName;
    @FXML private TableColumn<Supplier, String>    colSuppPhone;
    @FXML private TableColumn<Supplier, String>    colSuppEmail;
    @FXML private TableColumn<Supplier, String>    colSuppAddress;
    @FXML private TableColumn<Supplier, String>    colSuppStatus;
    @FXML private TableColumn<Supplier, Void>      colSuppActions;

    // ── Supplier Form ──────────────────────────────────────────────────────
    @FXML private TextField        txtName;
    @FXML private TextField        txtPhone;
    @FXML private TextField        txtEmail;
    @FXML private TextField        txtAddress;
    @FXML private ComboBox<String> cbStatus;   // ✅ String, not Status enum
    @FXML private Button           btnSave;
    @FXML private Button           btnClear;
    @FXML private Label            lblFormError;

    // ── Search ─────────────────────────────────────────────────────────────
    @FXML private TextField txtSearch;

    // ── SupplierProduct Table ──────────────────────────────────────────────
    @FXML private TableView<SupplierProduct>              tblLinks;
    @FXML private TableColumn<SupplierProduct, String>    colLinkProduct;
    @FXML private TableColumn<SupplierProduct, String>    colLinkPrice;
    @FXML private TableColumn<SupplierProduct, Integer>   colLinkLead;
    @FXML private TableColumn<SupplierProduct, Void>      colLinkActions;

    // ── Link Form ──────────────────────────────────────────────────────────
    @FXML private ComboBox<furniture_system.model.Product> cbProduct;
    @FXML private TextField txtImportPrice;
    @FXML private TextField txtLeadDays;
    @FXML private Label     lblLinkError;

    // ── Status bar ─────────────────────────────────────────────────────────
    @FXML private Label lblStatus;

    private final SupplierService service = new SupplierService();
    private Supplier editingSupplier = null;

    // ══════════════════════════════════════════════════════════════════════
    //  Initialize
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupSupplierTable();
        setupLinkTable();

        // ✅ Status is plain String — no enum
        cbStatus.setItems(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        cbStatus.setValue("ACTIVE");

        loadSuppliers();
        loadProductCombo();

        tblSuppliers.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, sel) -> {
                if (sel != null) populateForm(sel);
                loadLinkedProducts();
            });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Table Setup
    // ══════════════════════════════════════════════════════════════════════

    private void setupSupplierTable() {
        colSuppId.setCellValueFactory(new PropertyValueFactory<>("supplierId"));
        colSuppName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colSuppPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colSuppEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colSuppAddress.setCellValueFactory(new PropertyValueFactory<>("address"));

        // ✅ getStatus() returns String directly — no .name() needed
        colSuppStatus.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getStatus()));

        colSuppStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("ACTIVE".equals(item)
                    ? "-fx-text-fill:#2e7d32; -fx-font-weight:bold;"
                    : "-fx-text-fill:#c62828; -fx-font-weight:bold;");
            }
        });

        colSuppActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit  = new Button("✏ Edit");
            private final Button btnDeact = new Button("🗑 Deactivate");
            private final HBox   box      = new HBox(6, btnEdit, btnDeact);
            {
                box.setAlignment(Pos.CENTER);
                btnEdit.setStyle("-fx-background-color:#1565c0;-fx-text-fill:white;" +
                                 "-fx-background-radius:4;-fx-cursor:hand;-fx-font-size:11px;");
                btnDeact.setStyle("-fx-background-color:#c62828;-fx-text-fill:white;" +
                                  "-fx-background-radius:4;-fx-cursor:hand;-fx-font-size:11px;");
                btnEdit.setOnAction(e -> {
                    Supplier s = getTableView().getItems().get(getIndex());
                    editingSupplier = s;
                    populateForm(s);
                    btnSave.setText("Update");
                });
                btnDeact.setOnAction(e -> {
                    Supplier s = getTableView().getItems().get(getIndex());
                    handleDeactivate(s);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void setupLinkTable() {
        colLinkProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colLinkPrice.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getImportPrice().toPlainString()));
        colLinkLead.setCellValueFactory(new PropertyValueFactory<>("leadTimeDays"));

        colLinkActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnRemove = new Button("✖ Remove");
            {
                btnRemove.setStyle("-fx-background-color:#c62828;-fx-text-fill:white;" +
                                   "-fx-background-radius:4;-fx-cursor:hand;-fx-font-size:11px;");
                btnRemove.setOnAction(e -> {
                    SupplierProduct sp = getTableView().getItems().get(getIndex());
                    handleUnlink(sp);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btnRemove);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Load data
    // ══════════════════════════════════════════════════════════════════════

    private void loadSuppliers() {
        try {
            List<Supplier> list = txtSearch.getText().isBlank()
                ? service.getAllSuppliers()
                : service.searchSuppliers(txtSearch.getText());
            tblSuppliers.setItems(FXCollections.observableArrayList(list));
            setStatus("Loaded " + list.size() + " suppliers.", false);
        } catch (Exception e) {
            setStatus("Error: " + e.getMessage(), true);
        }
    }

    private void loadLinkedProducts() {
        Supplier sel = tblSuppliers.getSelectionModel().getSelectedItem();
        if (sel == null) { tblLinks.getItems().clear(); return; }
        try {
            tblLinks.setItems(FXCollections.observableArrayList(
                service.getLinkedProducts(sel.getSupplierId())));
        } catch (Exception e) {
            setStatus("Error loading links: " + e.getMessage(), true);
        }
    }

    private void loadProductCombo() {
        try {
            furniture_system.service.ProductService ps = new furniture_system.service.ProductService();
            cbProduct.setItems(FXCollections.observableArrayList(ps.getAllProducts()));
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Handlers
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void handleSearch() { loadSuppliers(); }

    @FXML
    public void handleSave() {
        lblFormError.setText("");
        try {
            Supplier s = editingSupplier != null ? editingSupplier : new Supplier();
            s.setName(txtName.getText().trim());
            s.setPhone(txtPhone.getText().trim());
            s.setEmail(txtEmail.getText().trim());
            s.setAddress(txtAddress.getText().trim());
            s.setStatus(cbStatus.getValue()); // ✅ String directly

            if (editingSupplier == null) {
                service.addSupplier(s);
                setStatus("✓ Supplier added successfully.", false);
            } else {
                service.updateSupplier(s);
                setStatus("✓ Supplier updated successfully.", false);
            }
            handleClear();
            loadSuppliers();
        } catch (IllegalArgumentException e) {
            lblFormError.setText(e.getMessage());
        } catch (Exception e) {
            lblFormError.setText("Unexpected error: " + e.getMessage());
        }
    }

    @FXML
    public void handleClear() {
        editingSupplier = null;
        txtName.clear(); txtPhone.clear(); txtEmail.clear(); txtAddress.clear();
        cbStatus.setValue("ACTIVE"); // ✅ String, not Status.ACTIVE
        btnSave.setText("Add Supplier");
        lblFormError.setText("");
    }

    private void handleDeactivate(Supplier s) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Deactivate supplier \"" + s.getName() + "\"?",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Deactivation");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                service.deactivateSupplier(s.getSupplierId());
                setStatus("✓ Supplier deactivated.", false);
                loadSuppliers();
            } catch (Exception e) {
                setStatus("Error: " + e.getMessage(), true);
            }
        }
    }

    @FXML
    public void handleLinkProduct() {
        lblLinkError.setText("");
        Supplier sel = tblSuppliers.getSelectionModel().getSelectedItem();
        if (sel == null) { lblLinkError.setText("Select a supplier first."); return; }
        furniture_system.model.Product product = cbProduct.getValue();
        if (product == null) { lblLinkError.setText("Select a product."); return; }
        try {
            BigDecimal price = new BigDecimal(txtImportPrice.getText().trim());
            int lead = txtLeadDays.getText().isBlank() ? 0
                       : Integer.parseInt(txtLeadDays.getText().trim());
            service.linkProduct(sel.getSupplierId(), product.getProductId(), price, lead);
            setStatus("✓ Product linked to supplier.", false);
            loadLinkedProducts();
            txtImportPrice.clear(); txtLeadDays.clear();
        } catch (NumberFormatException ex) {
            lblLinkError.setText("Import Price must be a valid number.");
        } catch (IllegalArgumentException ex) {
            lblLinkError.setText(ex.getMessage());
        } catch (Exception ex) {
            lblLinkError.setText("Error: " + ex.getMessage());
        }
    }

    private void handleUnlink(SupplierProduct sp) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Remove link between this supplier and \"" + sp.getProductName() + "\"?",
            ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                service.unlinkProduct(sp.getSupplierId(), sp.getProductId());
                loadLinkedProducts();
                setStatus("✓ Link removed.", false);
            } catch (Exception e) {
                setStatus("Error: " + e.getMessage(), true);
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void populateForm(Supplier s) {
        txtName.setText(s.getName());
        txtPhone.setText(s.getPhone());
        txtEmail.setText(s.getEmail() != null ? s.getEmail() : "");
        txtAddress.setText(s.getAddress() != null ? s.getAddress() : "");
        cbStatus.setValue(s.getStatus()); // ✅ String directly
        btnSave.setText("Update");
    }

    private void setStatus(String msg, boolean isError) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle(isError
            ? "-fx-text-fill:#c62828; -fx-font-size:12px;"
            : "-fx-text-fill:#2e7d32; -fx-font-size:12px;");
    }
}