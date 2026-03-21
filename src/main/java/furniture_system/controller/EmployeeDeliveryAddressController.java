package furniture_system.controller;

import furniture_system.dao.CustomerDAO;
import furniture_system.model.Customer;
import furniture_system.model.DeliveryAddress;
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

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Employee – Delivery Address Management.
 * Dùng standalone (sidebar) VÀ nhúng trong order creation.
 *
 * Standalone mode : currentCustomerId <= 0 → hiện TẤT CẢ địa chỉ,
 *                   Add Address mở dialog có ComboBox chọn customer.
 * Embedded mode   : currentCustomerId > 0  → lọc theo customer đó,
 *                   Add Address mở dialog không cần chọn customer.
 *                   Double-click hàng → fire onAddressSelected callback về order form.
 */
public class EmployeeDeliveryAddressController {

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<DeliveryAddress>            tblAddresses;
    @FXML private TableColumn<DeliveryAddress, Integer> colId;
    @FXML private TableColumn<DeliveryAddress, String>  colReceiver;
    @FXML private TableColumn<DeliveryAddress, String>  colPhone;
    @FXML private TableColumn<DeliveryAddress, String>  colAddress;
    @FXML private TableColumn<DeliveryAddress, String>  colDistrict;
    @FXML private TableColumn<DeliveryAddress, String>  colCity;
    @FXML private TableColumn<DeliveryAddress, Boolean> colDefault;

    // ── Toolbar ────────────────────────────────────────────────────────────
    @FXML private Button btnAddAddress;
    @FXML private Label  lblStatus;

    private final DeliveryAddressService          service     = new DeliveryAddressService();
    private final CustomerDAO                     customerDao = new CustomerDAO();
    private final ObservableList<DeliveryAddress> data        = FXCollections.observableArrayList();

    private int                       currentCustomerId = -1;
    private Consumer<DeliveryAddress> onAddressSelected; // callback → EmployeeOrderController

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupColumns();
        tblAddresses.setItems(data);
        loadAllAddresses();

        // Double-click → fire callback (chỉ có tác dụng trong embedded mode)
        tblAddresses.setRowFactory(tv -> {
            TableRow<DeliveryAddress> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    fireSelect(row.getItem());
            });
            return row;
        });
    }

    // ── Public API (dùng khi nhúng vào EmployeeOrderController) ───────────
    public void loadForCustomer(int customerId) {
        this.currentCustomerId = customerId;
        refreshTable();
    }

    public void setOnAddressSelected(Consumer<DeliveryAddress> callback) {
        this.onAddressSelected = callback;
    }

    public DeliveryAddress getSelectedAddress() {
        return tblAddresses.getSelectionModel().getSelectedItem();
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void setupColumns() {
        colId.setCellValueFactory(c ->
                new javafx.beans.property.SimpleIntegerProperty(
                        c.getValue().getAddressId()).asObject());
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

        tblAddresses.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(DeliveryAddress item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                setStyle(item.isDefault() ? "-fx-background-color:#eafaf1;" : "");
            }
        });
    }

    // ── Load & Refresh ─────────────────────────────────────────────────────
    private void loadAllAddresses() {
        try {
            data.setAll(service.getAll());
            setStatus("Loaded " + data.size() + " address(es).", false);
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Load Error", e.getMessage());
        }
    }

    private void refreshTable() {
        if (currentCustomerId <= 0) {
            loadAllAddresses();
            return;
        }
        try {
            data.setAll(service.getByCustomerId(currentCustomerId));
            setStatus(data.size() + " address(es) for customer #"
                    + currentCustomerId + ".", false);
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Load Error", e.getMessage());
        }
    }

    @FXML public void handleRefresh() { refreshTable(); }

    // ── Fire callback (chỉ dùng trong embedded mode) ──────────────────────
    private void fireSelect(DeliveryAddress addr) {
        if (onAddressSelected != null) onAddressSelected.accept(addr);
    }

    // ==================== ADD ADDRESS DIALOG ====================
    @FXML public void handleAddAddress() {
        if (currentCustomerId <= 0) {
            openAddDialogWithCustomerPicker();
        } else {
            openAddDialogForCustomer(currentCustomerId);
        }
    }

    // ── Dialog standalone: có ComboBox chọn customer ──────────────────────
    private void openAddDialogWithCustomerPicker() {
        List<Customer> customers;
        try {
            customers = customerDao.findAll();
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Load Error",
                    "Failed to load customers: " + e.getMessage());
            return;
        }

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Add New Delivery Address");

        ComboBox<Customer> cmbCustomer = new ComboBox<>(
                FXCollections.observableArrayList(customers));
        cmbCustomer.setMaxWidth(Double.MAX_VALUE);
        cmbCustomer.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Customer c) {
                return c == null ? "" : c.getCustomerId() + " – " + c.getFullName();
            }
            @Override public Customer fromString(String s) { return null; }
        });
        cmbCustomer.setPromptText("Select customer...");

        TextField tfReceiver = new TextField();
        TextField tfPhone    = new TextField();
        TextField tfAddrLine = new TextField();
        TextField tfWard     = new TextField();
        TextField tfDistrict = new TextField();
        TextField tfCity     = new TextField();
        CheckBox  chkDefault = new CheckBox("Set as Default Address");

        setPrompts(tfReceiver, tfPhone, tfAddrLine, tfWard, tfDistrict, tfCity);

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;");
        lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(sec("-- Customer"),            0, row, 2, 1); row++;
        grid.add(fl("Customer *"),     0, row); grid.add(cmbCustomer, 1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Receiver Info"),       0, row, 2, 1); row++;
        grid.add(fl("Receiver Name *"), 0, row); grid.add(tfReceiver, 1, row++);
        grid.add(fl("Phone *"),        0, row); grid.add(tfPhone,    1, row++);
        grid.add(new Label(""),         0, row); grid.add(hint("9–11 digits."), 1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Address Details"),     0, row, 2, 1); row++;
        grid.add(fl("Address Line *"), 0, row); grid.add(tfAddrLine, 1, row++);
        grid.add(fl("Ward *"),         0, row); grid.add(tfWard,     1, row++);
        grid.add(fl("District *"),     0, row); grid.add(tfDistrict, 1, row++);
        grid.add(fl("City *"),         0, row); grid.add(tfCity,     1, row++);
        grid.add(new Label(""),         0, row); grid.add(chkDefault, 1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(lblErr,                        0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Save Address");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave);
        btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            Customer sel = cmbCustomer.getValue();
            if (sel == null) { lblErr.setText("Please select a customer."); return; }
            try {
                DeliveryAddress addr = buildAddr(sel.getCustomerId(),
                        tfReceiver, tfPhone, tfAddrLine, tfWard, tfDistrict,
                        tfCity, chkDefault);
                service.addAddress(addr);
                setStatus("Address added for " + sel.getFullName() + ".", false);
                refreshTable();
                dlg.close();
            } catch (IllegalArgumentException ex) {
                lblErr.setText(ex.getMessage());
            } catch (SQLException ex) {
                lblErr.setText("DB Error: " + ex.getMessage());
            }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 500, 600));
        dlg.showAndWait();
    }

    // ── Dialog embedded: customer đã biết ────────────────────────────────
    private void openAddDialogForCustomer(int customerId) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Add New Delivery Address");

        TextField tfReceiver = new TextField();
        TextField tfPhone    = new TextField();
        TextField tfAddrLine = new TextField();
        TextField tfWard     = new TextField();
        TextField tfDistrict = new TextField();
        TextField tfCity     = new TextField();
        CheckBox  chkDefault = new CheckBox("Set as Default Address");

        setPrompts(tfReceiver, tfPhone, tfAddrLine, tfWard, tfDistrict, tfCity);

        Label lblForCustomer = new Label("Adding address for Customer #" + customerId);
        lblForCustomer.setStyle(
                "-fx-font-size:12px;-fx-text-fill:#1a237e;-fx-font-weight:bold;");

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;");
        lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblForCustomer,                0, row, 2, 1); row++;
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Receiver Info"),       0, row, 2, 1); row++;
        grid.add(fl("Receiver Name *"), 0, row); grid.add(tfReceiver, 1, row++);
        grid.add(fl("Phone *"),         0, row); grid.add(tfPhone,    1, row++);
        grid.add(new Label(""),          0, row); grid.add(hint("9–11 digits."), 1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Address Details"),     0, row, 2, 1); row++;
        grid.add(fl("Address Line *"), 0, row); grid.add(tfAddrLine, 1, row++);
        grid.add(fl("Ward *"),          0, row); grid.add(tfWard,     1, row++);
        grid.add(fl("District *"),      0, row); grid.add(tfDistrict, 1, row++);
        grid.add(fl("City *"),          0, row); grid.add(tfCity,     1, row++);
        grid.add(new Label(""),          0, row); grid.add(chkDefault, 1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(lblErr,                        0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Save Address");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave);
        btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                DeliveryAddress addr = buildAddr(customerId,
                        tfReceiver, tfPhone, tfAddrLine, tfWard, tfDistrict,
                        tfCity, chkDefault);
                service.addAddress(addr);
                setStatus("Address added for customer #" + customerId + ".", false);
                refreshTable();
                dlg.close();
            } catch (IllegalArgumentException ex) {
                lblErr.setText(ex.getMessage());
            } catch (SQLException ex) {
                lblErr.setText("DB Error: " + ex.getMessage());
            }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 520));
        dlg.setResizable(false);
        dlg.showAndWait();
    }

    // ── Shared builder ─────────────────────────────────────────────────────
    private DeliveryAddress buildAddr(int customerId,
                                       TextField tfReceiver, TextField tfPhone,
                                       TextField tfAddrLine, TextField tfWard,
                                       TextField tfDistrict, TextField tfCity,
                                       CheckBox chkDefault) {
        DeliveryAddress addr = new DeliveryAddress();
        addr.setCustomerId(customerId);
        addr.setReceiverName(tfReceiver.getText().trim());
        addr.setPhone(tfPhone.getText().trim());
        addr.setAddressLine(tfAddrLine.getText().trim());
        addr.setWard(tfWard.getText().trim());
        addr.setDistrict(tfDistrict.getText().trim());
        addr.setCity(tfCity.getText().trim());
        addr.setDefault(chkDefault.isSelected());
        return addr;
    }

    private void setPrompts(TextField rec, TextField ph, TextField addr,
                             TextField ward, TextField dist, TextField city) {
        rec.setPromptText("Full name of receiver");
        ph.setPromptText("e.g. 0901234567");
        addr.setPromptText("House number, street name");
        ward.setPromptText("e.g. Ward 1");
        dist.setPromptText("e.g. District 1");
        city.setPromptText("e.g. Ho Chi Minh City");
    }

    // ── UI helpers ─────────────────────────────────────────────────────────
    private GridPane buildGrid() {
        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(24));
        g.getColumnConstraints().addAll(
                new ColumnConstraints(145), new ColumnConstraints(280));
        return g;
    }

    private Label fl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;");
        return l;
    }
    private Label sec(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;");
        return l;
    }
    private Label hint(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size:10px;-fx-text-fill:#888;");
        return l;
    }
    private Button primaryBtn(String t) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;" +
                   "-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
        return b;
    }
    private void setStatus(String msg, boolean isError) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle(isError
                ? "-fx-text-fill:#c62828;-fx-font-size:12px;"
                : "-fx-text-fill:#37474f;-fx-font-size:12px;");
    }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t);
        a.setTitle(title); a.setHeaderText(null);
        a.setContentText(msg); a.showAndWait();
    }
}