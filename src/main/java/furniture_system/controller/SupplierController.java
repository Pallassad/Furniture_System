package furniture_system.controller;

import furniture_system.model.Product;
import furniture_system.model.Supplier;
import furniture_system.model.SupplierProduct;
import furniture_system.service.ProductService;
import furniture_system.service.SupplierService;
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
import java.util.List;

public class SupplierController {

    // ── Supplier Table ─────────────────────────────────────────────────────
    @FXML private TableView<Supplier>            tblSuppliers;
    @FXML private TableColumn<Supplier, Integer> colSuppId;
    @FXML private TableColumn<Supplier, String>  colSuppName;
    @FXML private TableColumn<Supplier, String>  colSuppPhone;
    @FXML private TableColumn<Supplier, String>  colSuppEmail;
    @FXML private TableColumn<Supplier, String>  colSuppAddress;
    @FXML private TableColumn<Supplier, String>  colSuppStatus;

    // ── Search & Toolbar ───────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Button    btnAdd;
    @FXML private Button    btnEdit;
    @FXML private Button    btnDeactivate;
    @FXML private Button    btnDelete;
    @FXML private Button    btnManageLinks;
    @FXML private Label     lblStatus;

    private final SupplierService            service  = new SupplierService();
    private final ObservableList<Supplier>   data     = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupSupplierTable();
        loadSuppliers();

        tblSuppliers.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    boolean has = sel != null;
                    btnEdit.setDisable(!has);
                    btnManageLinks.setDisable(!has);
                    boolean canDeact = has && "ACTIVE".equals(sel.getStatus());
                    btnDeactivate.setDisable(!canDeact);
                    btnDelete.setDisable(!has);
                });
        btnEdit.setDisable(true);
        btnDeactivate.setDisable(true);
        btnDelete.setDisable(true);
        btnManageLinks.setDisable(true);
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void setupSupplierTable() {
        colSuppId.setCellValueFactory(c ->
                new javafx.beans.property.SimpleIntegerProperty(c.getValue().getSupplierId()).asObject());
        colSuppName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getName()));
        colSuppPhone.setCellValueFactory(c ->
                new SimpleStringProperty(nvl(c.getValue().getPhone())));
        colSuppEmail.setCellValueFactory(c ->
                new SimpleStringProperty(nvl(c.getValue().getEmail())));
        colSuppAddress.setCellValueFactory(c ->
                new SimpleStringProperty(nvl(c.getValue().getAddress())));
        colSuppStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus()));

        colSuppStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle("ACTIVE".equals(s)
                        ? "-fx-text-fill:#27ae60;-fx-font-weight:bold;"
                        : "-fx-text-fill:#c62828;-fx-font-weight:bold;");
            }
        });

        tblSuppliers.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Supplier item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                setStyle("INACTIVE".equals(item.getStatus())
                        ? "-fx-background-color:#e8e8e8;" : "");
            }
        });

        tblSuppliers.setItems(data);
    }

    // ── Load & Search ──────────────────────────────────────────────────────
    private void loadSuppliers() {
        try {
            List<Supplier> list = txtSearch.getText().isBlank()
                    ? service.getAllSuppliers()
                    : service.searchSuppliers(txtSearch.getText());
            data.setAll(list);
            setStatus("Loaded " + list.size() + " supplier(s).", false);
        } catch (Exception e) { setStatus("Error: " + e.getMessage(), true); }
    }

    @FXML public void handleSearch()  { loadSuppliers(); }
    @FXML public void handleRefresh() { txtSearch.clear(); loadSuppliers(); }

    // ==================== ADD ====================
    @FXML public void handleAdd() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Add New Supplier");

        TextField        tfName    = new TextField();
        TextField        tfPhone   = new TextField();
        TextField        tfEmail   = new TextField();
        TextField        tfAddress = new TextField();
        ComboBox<String> cbStatus  = new ComboBox<>(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        cbStatus.setValue("ACTIVE"); cbStatus.setMaxWidth(Double.MAX_VALUE);

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(sec("-- Supplier Info"),      0, row, 2, 1); row++;
        grid.add(fl("Name *"),      0, row); grid.add(tfName,    1, row++);
        grid.add(new Label(""),     0, row); grid.add(hint("Min 2 characters."), 1, row++);
        grid.add(fl("Phone *"),     0, row); grid.add(tfPhone,   1, row++);
        grid.add(new Label(""),     0, row); grid.add(hint("9–11 digits, must be unique."), 1, row++);
        grid.add(fl("Email"),       0, row); grid.add(tfEmail,   1, row++);
        grid.add(new Label(""),     0, row); grid.add(hint("Optional. Must be unique."), 1, row++);
        grid.add(fl("Address"),     0, row); grid.add(tfAddress, 1, row++);
        grid.add(fl("Status *"),    0, row); grid.add(cbStatus,  1, row++);
        grid.add(new Separator(),              0, row, 2, 1); row++;
        grid.add(lblErr,                       0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Add Supplier");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                Supplier s = new Supplier();
                s.setName(tfName.getText().trim());
                s.setPhone(tfPhone.getText().trim());
                s.setEmail(tfEmail.getText().trim().isEmpty() ? null : tfEmail.getText().trim());
                s.setAddress(tfAddress.getText().trim().isEmpty() ? null : tfAddress.getText().trim());
                s.setStatus(cbStatus.getValue());
                service.addSupplier(s);
                setStatus("Supplier [" + s.getName() + "] added.", false);
                loadSuppliers(); dlg.close();
            } catch (IllegalArgumentException ex) { lblErr.setText(ex.getMessage()); }
              catch (Exception ex) { lblErr.setText("Error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 420));
        dlg.setResizable(false); dlg.showAndWait();
    }

    // ==================== EDIT ====================
    @FXML public void handleEdit() {
        Supplier sel = tblSuppliers.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Supplier - " + sel.getName());

        TextField        tfName    = new TextField(sel.getName());
        TextField        tfPhone   = new TextField(nvl(sel.getPhone()));
        TextField        tfEmail   = new TextField(sel.getEmail() != null ? sel.getEmail() : "");
        TextField        tfAddress = new TextField(sel.getAddress() != null ? sel.getAddress() : "");
        ComboBox<String> cbStatus  = new ComboBox<>(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        cbStatus.setValue(sel.getStatus()); cbStatus.setMaxWidth(Double.MAX_VALUE);

        Label lblInfo = new Label("Supplier ID: " + sel.getSupplierId());
        lblInfo.setStyle("-fx-text-fill:#555;-fx-font-size:12px;");
        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblInfo,              0, row, 2, 1); row++;
        grid.add(new Separator(),      0, row, 2, 1); row++;
        grid.add(sec("-- Supplier Info"), 0, row, 2, 1); row++;
        grid.add(fl("Name *"),  0, row); grid.add(tfName,    1, row++);
        grid.add(fl("Phone *"), 0, row); grid.add(tfPhone,   1, row++);
        grid.add(fl("Email"),   0, row); grid.add(tfEmail,   1, row++);
        grid.add(fl("Address"), 0, row); grid.add(tfAddress, 1, row++);
        grid.add(fl("Status *"),0, row); grid.add(cbStatus,  1, row++);
        grid.add(new Separator(),      0, row, 2, 1); row++;
        grid.add(lblErr,               0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Save Changes");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                sel.setName(tfName.getText().trim());
                sel.setPhone(tfPhone.getText().trim());
                sel.setEmail(tfEmail.getText().trim().isEmpty() ? null : tfEmail.getText().trim());
                sel.setAddress(tfAddress.getText().trim().isEmpty() ? null : tfAddress.getText().trim());
                sel.setStatus(cbStatus.getValue());
                service.updateSupplier(sel);
                setStatus("Supplier [" + sel.getName() + "] updated.", false);
                loadSuppliers(); dlg.close();
            } catch (IllegalArgumentException ex) { lblErr.setText(ex.getMessage()); }
              catch (Exception ex) { lblErr.setText("Error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 380));
        dlg.setResizable(false); dlg.showAndWait();
    }

    // ==================== DEACTIVATE ====================
    @FXML public void handleDeactivate() {
        Supplier sel = tblSuppliers.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Deactivate supplier \"" + sel.getName() + "\"?\n\n"
                + "They will no longer appear in active supplier lists.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Deactivation"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                service.deactivateSupplier(sel.getSupplierId());
                setStatus("Supplier [" + sel.getName() + "] deactivated.", false);
                loadSuppliers();
            } catch (Exception ex) { alert(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
        });
    }

    // ==================== HARD DELETE ====================
    @FXML public void handleDelete() {
        Supplier sel = tblSuppliers.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Xoá vĩnh viễn nhà cung cấp \"" + sel.getName() + "\"?\n\n"
                + "⚠ Hành động này không thể hoàn tác.\n"
                + "Yêu cầu: không còn sản phẩm nào liên kết với nhà cung cấp này.\n"
                + "Các liên kết SupplierProduct sẽ bị xoá theo.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận xoá nhà cung cấp"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                service.deleteSupplier(sel.getSupplierId());
                setStatus("Đã xoá vĩnh viễn nhà cung cấp [" + sel.getName() + "].", false);
                loadSuppliers();
            } catch (IllegalStateException ex) {
                alert(Alert.AlertType.ERROR, "Không thể xoá", ex.getMessage());
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Lỗi xoá", ex.getMessage());
            }
        });
    }

    // ==================== MANAGE LINKS DIALOG ====================
    @FXML public void handleManageLinks() {
        Supplier sel = tblSuppliers.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Manage Products - " + sel.getName());

        // ── Linked products table ──
        TableView<SupplierProduct>              tblLinks    = new TableView<>();
        TableColumn<SupplierProduct, String>    cProd       = new TableColumn<>("Product");
        TableColumn<SupplierProduct, String>    cPrice      = new TableColumn<>("Import Price");
        TableColumn<SupplierProduct, Integer>   cLead       = new TableColumn<>("Lead Days");
        TableColumn<SupplierProduct, Void>      cRemove     = new TableColumn<>("");

        cProd.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));
        cProd.setPrefWidth(230);
        cPrice.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%,.0f ₫", c.getValue().getImportPrice())));
        cPrice.setPrefWidth(130);
        cLead.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("leadTimeDays"));
        cLead.setPrefWidth(90);
        cRemove.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✖ Remove");
            { btn.setStyle("-fx-background-color:#c62828;-fx-text-fill:white;" +
                           "-fx-background-radius:4;-fx-cursor:hand;-fx-font-size:11px;");
              btn.setOnAction(e -> {
                  SupplierProduct sp = getTableView().getItems().get(getIndex());
                  Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                          "Remove link to \"" + sp.getProductName() + "\"?",
                          ButtonType.YES, ButtonType.NO);
                  c.setHeaderText(null);
                  c.showAndWait().ifPresent(b -> {
                      if (b != ButtonType.YES) return;
                      try { service.unlinkProduct(sp.getSupplierId(), sp.getProductId());
                            loadLinks(sel.getSupplierId(), tblLinks);
                      } catch (Exception ex) { alert(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
                  });
              }); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : btn); }
        });
        cRemove.setPrefWidth(100);
        tblLinks.getColumns().addAll(cProd, cPrice, cLead, cRemove);
        tblLinks.setPrefHeight(200);
        loadLinks(sel.getSupplierId(), tblLinks);

        // ── Link form ──
        ComboBox<Product> cbProduct      = new ComboBox<>();
        TextField         tfImportPrice  = new TextField();
        TextField         tfLeadDays     = new TextField();
        cbProduct.setMaxWidth(Double.MAX_VALUE);
        tfImportPrice.setPromptText("e.g. 250000");
        tfLeadDays.setPromptText("0");

        try {
            ProductService ps = new ProductService();
            cbProduct.setItems(FXCollections.observableArrayList(ps.getAllProducts()));
        } catch (Exception ignored) {}

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;

        Label lblTitle = new Label("Supplier: " + sel.getName() + "  (ID: " + sel.getSupplierId() + ")");
        lblTitle.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1a237e;");
        grid.add(lblTitle,                          0, row, 2, 1); row++;
        grid.add(new Separator(),                   0, row, 2, 1); row++;
        grid.add(sec("-- Linked Products"),         0, row, 2, 1); row++;
        grid.add(tblLinks,                          0, row, 2, 1); row++;
        grid.add(new Separator(),                   0, row, 2, 1); row++;
        grid.add(sec("-- Link New Product"),        0, row, 2, 1); row++;
        grid.add(fl("Product *"),        0, row); grid.add(cbProduct,     1, row++);
        grid.add(fl("Import Price *"),   0, row); grid.add(tfImportPrice, 1, row++);
        grid.add(fl("Lead Days"),        0, row); grid.add(tfLeadDays,    1, row++);
        grid.add(new Label(""),          0, row); grid.add(hint("Days from order to delivery. Default 0."), 1, row++);
        grid.add(new Separator(),                   0, row, 2, 1); row++;
        grid.add(lblErr,                            0, row, 2, 1); row++;

        Button btnLink   = primaryBtn("Link Product");
        Button btnClose  = new Button("Close");
        btnClose.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnClose, btnLink); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnLink.setOnAction(ev -> {
            lblErr.setText("");
            if (cbProduct.getValue() == null) { lblErr.setText("Please select a product."); return; }
            try {
                BigDecimal price = new BigDecimal(tfImportPrice.getText().trim().replace(",", ""));
                int lead = tfLeadDays.getText().isBlank() ? 0 : Integer.parseInt(tfLeadDays.getText().trim());
                service.linkProduct(sel.getSupplierId(), cbProduct.getValue().getProductId(), price, lead);
                setStatus("Product linked to [" + sel.getName() + "].", false);
                loadLinks(sel.getSupplierId(), tblLinks);
                cbProduct.setValue(null); tfImportPrice.clear(); tfLeadDays.clear();
            } catch (NumberFormatException ex) { lblErr.setText("Import Price must be a valid number."); }
              catch (IllegalArgumentException ex) { lblErr.setText(ex.getMessage()); }
              catch (Exception ex) { lblErr.setText("Error: " + ex.getMessage()); }
        });
        btnClose.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 520, 660)); dlg.showAndWait();
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private void loadLinks(int supplierId, TableView<SupplierProduct> tbl) {
        try {
            tbl.setItems(FXCollections.observableArrayList(service.getLinkedProducts(supplierId)));
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); }
    }

    private GridPane buildGrid() {
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(24));
        g.getColumnConstraints().addAll(new ColumnConstraints(145), new ColumnConstraints(290));
        return g;
    }

    private Label fl(String t)   { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
    private Label hint(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:10px;-fx-text-fill:#888;"); return l; }
    private Button primaryBtn(String t) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
        return b;
    }
    private void setStatus(String msg, boolean isError) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle(isError ? "-fx-text-fill:#c62828;-fx-font-size:12px;"
                                   : "-fx-text-fill:#2e7d32;-fx-font-size:12px;");
    }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
    private static String nvl(String s) { return s != null ? s : "—"; }
}