package furniture_system.controller;

import furniture_system.model.Customer;
import furniture_system.model.DeliveryAddress;
import furniture_system.dao.CustomerDAO;
import furniture_system.service.DeliveryAddressService;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

public class AdminDeliveryAddressController {

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<DeliveryAddress>            tblAddresses;
    @FXML private TableColumn<DeliveryAddress, Integer> colId;
    @FXML private TableColumn<DeliveryAddress, String>  colCustomer;
    @FXML private TableColumn<DeliveryAddress, String>  colReceiver;
    @FXML private TableColumn<DeliveryAddress, String>  colPhone;
    @FXML private TableColumn<DeliveryAddress, String>  colAddress;
    @FXML private TableColumn<DeliveryAddress, String>  colDistrict;
    @FXML private TableColumn<DeliveryAddress, String>  colCity;
    @FXML private TableColumn<DeliveryAddress, Boolean> colDefault;
    @FXML private TableColumn<DeliveryAddress, String>  colStatus;

    // ── Search & Toolbar ───────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Button    btnAdd;
    @FXML private Button    btnEdit;
    @FXML private Button    btnSetDefault;
    @FXML private Button    btnDelete;
    @FXML private Label     lblStatus;

    // ── Stats ──────────────────────────────────────────────────────────────
    @FXML private Label lblTopCity;
    @FXML private Label lblDefaultRate;

    private final DeliveryAddressService          service     = new DeliveryAddressService();
    private final CustomerDAO                     customerDao = new CustomerDAO();
    private final ObservableList<DeliveryAddress> data        = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupColumns();
        tblAddresses.setItems(data);
        loadTable();
        loadStats();

        tblAddresses.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    boolean has = sel != null;
                    btnEdit.setDisable(!has);
                    btnSetDefault.setDisable(!has || sel.isDefault());
                    // Delete only enabled when INACTIVE
                    boolean canDelete = has && "INACTIVE".equals(sel.getStatus());
                    btnDelete.setDisable(!canDelete);
                });
        btnEdit.setDisable(true);
        btnSetDefault.setDisable(true);
        btnDelete.setDisable(true);
    }

    // ── Column setup ───────────────────────────────────────────────────────
    private void setupColumns() {
        colId.setCellValueFactory(c ->
                new javafx.beans.property.SimpleIntegerProperty(c.getValue().getAddressId()).asObject());
        colCustomer.setCellValueFactory(c ->
                new SimpleStringProperty(nvl(c.getValue().getCustomerName())));
        colReceiver.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getReceiverName()));
        colPhone.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPhone()));
        colAddress.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAddressLine()));
        colDistrict.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDistrict()));
        colCity.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getCity()));
        colDefault.setCellValueFactory(c ->
                new SimpleBooleanProperty(c.getValue().isDefault()));
        colDefault.setCellFactory(CheckBoxTableCell.forTableColumn(colDefault));
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus()));

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle("ACTIVE".equals(s)
                        ? "-fx-text-fill:#27ae60;-fx-font-weight:bold;"
                        : "-fx-text-fill:#c62828;-fx-font-weight:bold;");
            }
        });

        tblAddresses.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(DeliveryAddress item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                if ("INACTIVE".equals(item.getStatus()))
                    setStyle("-fx-background-color:#e8e8e8;");
                else if (item.isDefault())
                    setStyle("-fx-background-color:#e8f5e9;");
                else setStyle("");
            }
        });
    }

    // ── Load & Search ──────────────────────────────────────────────────────
    private void loadTable() {
        try {
            data.setAll(service.getAll());
            setStatus("Loaded " + data.size() + " address(es).", false);
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Load Error", e.getMessage()); }
    }

    private void loadStats() {
        try {
            List<Object[]> byCity = service.getOrdersByCity();
            if (!byCity.isEmpty()) {
                Object[] top = byCity.get(0);
                lblTopCity.setText("Top City: " + top[0] + " (" + top[1] + " orders)");
            } else lblTopCity.setText("Top City: N/A");

            List<Object[]> usage = service.getDefaultUsageRate();
            long def = 0, nonDef = 0;
            for (Object[] row : usage) {
                if ("Default".equals(row[0]))     def    = ((Number) row[1]).longValue();
                if ("Non-Default".equals(row[0])) nonDef = ((Number) row[1]).longValue();
            }
            long total = def + nonDef;
            lblDefaultRate.setText(total > 0
                    ? "Default address used: " + (def * 100 / total) + "% of orders"
                    : "Default address used: N/A");
        } catch (Exception e) { lblTopCity.setText("Stats unavailable"); }
    }

    @FXML public void handleRefresh() { txtSearch.clear(); loadTable(); loadStats(); }

    @FXML public void onSearch() {
        try {
            data.setAll(service.search(txtSearch.getText()));
            setStatus(data.size() + " result(s).", false);
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Search Error", e.getMessage()); }
    }

    // ==================== ADD DIALOG ====================
    @FXML public void onAdd() {
        List<Customer> customers = loadCustomers();
        if (customers == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Add New Delivery Address");

        ComboBox<Customer> cmbCustomer = buildCustomerCombo(customers);
        TextField tfReceiver = new TextField();
        TextField tfPhone    = new TextField();
        TextField tfAddrLine = new TextField();
        TextField tfWard     = new TextField();
        TextField tfDistrict = new TextField();
        TextField tfCity     = new TextField();
        CheckBox  chkDefault = new CheckBox("Set as Default Address");
        ComboBox<String> cbStatus = new ComboBox<>(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        cbStatus.setValue("ACTIVE"); cbStatus.setMaxWidth(Double.MAX_VALUE);

        setAddressPrompts(tfReceiver, tfPhone, tfAddrLine, tfWard, tfDistrict, tfCity);

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(sec("-- Customer"),            0, row, 2, 1); row++;
        grid.add(fl("Customer *"),     0, row); grid.add(cmbCustomer, 1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Receiver Info"),       0, row, 2, 1); row++;
        grid.add(fl("Receiver Name *"), 0, row); grid.add(tfReceiver, 1, row++);
        grid.add(fl("Phone *"),        0, row); grid.add(tfPhone,    1, row++);
        grid.add(new Label(""),         0, row); grid.add(hint("9-11 digits."), 1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Address"),             0, row, 2, 1); row++;
        grid.add(fl("Address Line *"), 0, row); grid.add(tfAddrLine, 1, row++);
        grid.add(fl("Ward *"),         0, row); grid.add(tfWard,     1, row++);
        grid.add(fl("District *"),     0, row); grid.add(tfDistrict, 1, row++);
        grid.add(fl("City *"),         0, row); grid.add(tfCity,     1, row++);
        grid.add(new Label(""),         0, row); grid.add(chkDefault, 1, row++);
        grid.add(fl("Status *"),       0, row); grid.add(cbStatus,   1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(lblErr,                        0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Add Address");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                DeliveryAddress addr = buildAddress(null, cmbCustomer, tfReceiver, tfPhone,
                        tfAddrLine, tfWard, tfDistrict, tfCity, chkDefault, cbStatus, lblErr);
                if (addr == null) return;
                service.addAddress(addr);
                setStatus("Address added for " + nvl(addr.getReceiverName()) + ".", false);
                loadTable(); loadStats(); dlg.close();
            } catch (IllegalArgumentException ex) { lblErr.setText(ex.getMessage()); }
              catch (Exception ex) { lblErr.setText("DB Error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 500, 600)); dlg.showAndWait();
    }

    // ==================== EDIT DIALOG ====================
    @FXML public void onEdit() {
        DeliveryAddress sel = tblAddresses.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        List<Customer> customers = loadCustomers();
        if (customers == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Address #" + sel.getAddressId());

        ComboBox<Customer> cmbCustomer = buildCustomerCombo(customers);
        customers.stream().filter(c -> c.getCustomerId() == sel.getCustomerId())
                .findFirst().ifPresent(cmbCustomer::setValue);

        TextField tfReceiver = new TextField(nvl(sel.getReceiverName()));
        TextField tfPhone    = new TextField(nvl(sel.getPhone()));
        TextField tfAddrLine = new TextField(nvl(sel.getAddressLine()));
        TextField tfWard     = new TextField(nvl(sel.getWard()));
        TextField tfDistrict = new TextField(nvl(sel.getDistrict()));
        TextField tfCity     = new TextField(nvl(sel.getCity()));
        CheckBox  chkDefault = new CheckBox("Set as Default Address");
        chkDefault.setSelected(sel.isDefault());
        ComboBox<String> cbStatus = new ComboBox<>(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        cbStatus.setValue(sel.getStatus()); cbStatus.setMaxWidth(Double.MAX_VALUE);

        Label lblInfo = new Label("Address ID: " + sel.getAddressId()
                + "   |   Customer: " + nvl(sel.getCustomerName()));
        lblInfo.setStyle("-fx-text-fill:#555;-fx-font-size:12px;");
        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblInfo,                       0, row, 2, 1); row++;
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Customer"),            0, row, 2, 1); row++;
        grid.add(fl("Customer *"),     0, row); grid.add(cmbCustomer, 1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Receiver Info"),       0, row, 2, 1); row++;
        grid.add(fl("Receiver Name *"), 0, row); grid.add(tfReceiver, 1, row++);
        grid.add(fl("Phone *"),        0, row); grid.add(tfPhone,    1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Address"),             0, row, 2, 1); row++;
        grid.add(fl("Address Line *"), 0, row); grid.add(tfAddrLine, 1, row++);
        grid.add(fl("Ward *"),         0, row); grid.add(tfWard,     1, row++);
        grid.add(fl("District *"),     0, row); grid.add(tfDistrict, 1, row++);
        grid.add(fl("City *"),         0, row); grid.add(tfCity,     1, row++);
        grid.add(new Label(""),         0, row); grid.add(chkDefault, 1, row++);
        grid.add(fl("Status *"),       0, row); grid.add(cbStatus,   1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(lblErr,                        0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Save Changes");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave);
        btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                DeliveryAddress addr = buildAddress(sel.getAddressId(), cmbCustomer, tfReceiver,
                        tfPhone, tfAddrLine, tfWard, tfDistrict, tfCity, chkDefault, cbStatus, lblErr);
                if (addr == null) return;
                service.updateAddress(addr);
                setStatus("Address #" + sel.getAddressId() + " updated.", false);
                loadTable(); loadStats(); dlg.close();
            } catch (IllegalArgumentException ex) { lblErr.setText(ex.getMessage()); }
              catch (Exception ex) { lblErr.setText("DB Error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 500, 600)); dlg.showAndWait();
    }

    // ==================== SET DEFAULT ====================
    @FXML public void onSetDefault() {
        DeliveryAddress sel = tblAddresses.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (sel.isDefault()) {
            alert(Alert.AlertType.INFORMATION, "Already Default",
                    "This address is already the default."); return;
        }
        try {
            service.setDefault(sel.getAddressId(), sel.getCustomerId());
            setStatus("Default address updated for customer #" + sel.getCustomerId() + ".", false);
            loadTable(); loadStats();
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); }
    }

    // ==================== DELETE ====================
    @FXML public void onDelete() {
        DeliveryAddress sel = tblAddresses.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete address for [" + nvl(sel.getReceiverName()) + "]?\n"
                + sel.getAddressLine() + ", " + sel.getCity() + "\n\n"
                + "This action cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete Address");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                service.deleteAddress(sel.getAddressId());
                setStatus("Address #" + sel.getAddressId() + " permanently deleted.", false);
                loadTable(); loadStats();
            } catch (IllegalStateException ex) {
                alert(Alert.AlertType.ERROR, "Cannot Delete", ex.getMessage());
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });
    }

    // ── Shared helpers ─────────────────────────────────────────────────────
    private List<Customer> loadCustomers() {
        try { return customerDao.findAll(); }
        catch (RuntimeException e) {
            alert(Alert.AlertType.ERROR, "Load Error", "Failed to load customers: " + e.getMessage());
            return null;
        }
    }

    private ComboBox<Customer> buildCustomerCombo(List<Customer> customers) {
        ComboBox<Customer> cb = new ComboBox<>(FXCollections.observableArrayList(customers));
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Customer c) {
                return c == null ? "" : c.getCustomerId() + " - " + c.getFullName(); }
            @Override public Customer fromString(String s) { return null; }
        });
        return cb;
    }

    private void setAddressPrompts(TextField rec, TextField ph, TextField addr,
                                    TextField ward, TextField dist, TextField city) {
        rec.setPromptText("Full name of receiver"); ph.setPromptText("e.g. 0901234567");
        addr.setPromptText("House number, street name"); ward.setPromptText("e.g. Ward 1");
        dist.setPromptText("e.g. District 1"); city.setPromptText("e.g. Ho Chi Minh City");
    }

    private DeliveryAddress buildAddress(Integer id, ComboBox<Customer> cmbCustomer,
                                          TextField tfRec, TextField tfPh,
                                          TextField tfAddr, TextField tfWard,
                                          TextField tfDist, TextField tfCity,
                                          CheckBox chkDef, ComboBox<String> cbStatus,
                                          Label lblErr) {
        if (cmbCustomer.getValue() == null) { lblErr.setText("Customer is required."); return null; }
        DeliveryAddress a = new DeliveryAddress();
        if (id != null) a.setAddressId(id);
        a.setCustomerId(cmbCustomer.getValue().getCustomerId());
        a.setReceiverName(tfRec.getText().trim());
        a.setPhone(tfPh.getText().trim());
        a.setAddressLine(tfAddr.getText().trim());
        a.setWard(tfWard.getText().trim());
        a.setDistrict(tfDist.getText().trim());
        a.setCity(tfCity.getText().trim());
        a.setDefault(chkDef.isSelected());
        a.setStatus(cbStatus.getValue() != null ? cbStatus.getValue() : "ACTIVE");
        return a;
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
                                   : "-fx-text-fill:#37474f;-fx-font-size:12px;");
    }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
    private static String nvl(String s) { return s != null ? s : "-"; }
}