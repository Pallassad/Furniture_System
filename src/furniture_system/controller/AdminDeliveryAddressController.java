package furniture_system.controller;

import furniture_system.model.Customer;
import furniture_system.model.DeliveryAddress;
import furniture_system.dao.CustomerDAO;
import furniture_system.service.DeliveryAddressService;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * AdminDeliveryAddressController
 *
 * Handles the admin_delivery_address_management.fxml view.
 * Responsibilities:
 *  - View all delivery addresses (all statuses)
 *  - Add new address
 *  - Update existing address
 *  - Soft-delete (deactivate) address
 *  - Search by keyword
 *  - Basic stats display
 */
public class AdminDeliveryAddressController implements Initializable {

    // ── Table ────────────────────────────────────────────────────────────────
    @FXML private TableView<DeliveryAddress>            tblAddresses;
    @FXML private TableColumn<DeliveryAddress, Integer> colId;
    @FXML private TableColumn<DeliveryAddress, String>  colCustomer;
    @FXML private TableColumn<DeliveryAddress, String>  colReceiver;
    @FXML private TableColumn<DeliveryAddress, String>  colPhone;
    @FXML private TableColumn<DeliveryAddress, String>  colAddress;
    @FXML private TableColumn<DeliveryAddress, String>  colWard;
    @FXML private TableColumn<DeliveryAddress, String>  colDistrict;
    @FXML private TableColumn<DeliveryAddress, String>  colCity;
    @FXML private TableColumn<DeliveryAddress, Boolean> colDefault;
    @FXML private TableColumn<DeliveryAddress, String>  colStatus;

    // ── Search ───────────────────────────────────────────────────────────────
    @FXML private TextField     txtSearch;

    // ── Form panel ───────────────────────────────────────────────────────────
    @FXML private VBox          formPanel;
    @FXML private Label         lblFormTitle;
    @FXML private ComboBox<Customer> cmbCustomer;
    @FXML private TextField     txtReceiverName;
    @FXML private TextField     txtPhone;
    @FXML private TextField     txtAddressLine;
    @FXML private TextField     txtWard;
    @FXML private TextField     txtDistrict;
    @FXML private TextField     txtCity;
    @FXML private CheckBox      chkDefault;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private Label         lblError;

    // ── Stats labels ─────────────────────────────────────────────────────────
    @FXML private Label         lblTopCity;
    @FXML private Label         lblDefaultRate;

    // ── State ────────────────────────────────────────────────────────────────
    private final DeliveryAddressService service  = new DeliveryAddressService();
    private final CustomerDAO            customerDao = new CustomerDAO();
    private final ObservableList<DeliveryAddress> data = FXCollections.observableArrayList();
    private DeliveryAddress selectedAddress = null;
    private boolean isEditMode = false;

    // ─────────────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        setupFormComboBoxes();
        tblAddresses.setItems(data);

        // Row selection → populate form for edit
        tblAddresses.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> { if (sel != null) populateForm(sel); });

        loadTable();
        loadStats();
        hideForm();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TABLE
    // ─────────────────────────────────────────────────────────────────────────

    private void setupColumns() {
        colId.setCellValueFactory(c ->
                new SimpleIntegerProperty(c.getValue().getAddressId()).asObject());
        colCustomer.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getCustomerName()));
        colReceiver.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getReceiverName()));
        colPhone.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPhone()));
        colAddress.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAddressLine()));
        colWard.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getWard()));
        colDistrict.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDistrict()));
        colCity.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getCity()));
        colDefault.setCellValueFactory(c ->
                new SimpleBooleanProperty(c.getValue().isDefault()));
        colDefault.setCellFactory(CheckBoxTableCell.forTableColumn(colDefault));
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus()));

        // Colour-code Status cell
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("ACTIVE".equals(item)
                        ? "-fx-text-fill: #27ae60; -fx-font-weight: bold;"
                        : "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            }
        });
    }

    private void loadTable() {
        try {
            data.setAll(service.getAll());
        } catch (SQLException | RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Load Error", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEARCH
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onSearch() {
        String kw = txtSearch.getText();
        try {
            data.setAll(service.search(kw));
        } catch (SQLException | RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Search Error", e.getMessage());
        }
    }

    @FXML
    private void onClearSearch() {
        txtSearch.clear();
        loadTable();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADD
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onAdd() {
        isEditMode = false;
        selectedAddress = null;
        lblFormTitle.setText("Add New Delivery Address");
        clearForm();
        cmbStatus.setValue("ACTIVE");
        showForm();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EDIT
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onEdit() {
        selectedAddress = tblAddresses.getSelectionModel().getSelectedItem();
        if (selectedAddress == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an address to edit.");
            return;
        }
        isEditMode = true;
        lblFormTitle.setText("Edit Delivery Address #" + selectedAddress.getAddressId());
        populateForm(selectedAddress);
        showForm();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE (soft)
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onDelete() {
        DeliveryAddress sel = tblAddresses.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an address to deactivate.");
            return;
        }
        if ("INACTIVE".equals(sel.getStatus())) {
            showAlert(Alert.AlertType.INFORMATION, "Already Inactive",
                    "This address is already inactive.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Deactivate address for " + sel.getReceiverName() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Deactivation");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    service.deleteAddress(sel.getAddressId());
                    loadTable();
                    loadStats();
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Address deactivated.");
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FORM SAVE
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onSave() {
        lblError.setText("");
        try {
            DeliveryAddress addr = buildFromForm();
            if (isEditMode) {
                addr.setAddressId(selectedAddress.getAddressId());
                service.updateAddress(addr);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Address updated successfully.");
            } else {
                service.addAddress(addr);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Address added successfully.");
            }
            hideForm();
            loadTable();
            loadStats();
        } catch (IllegalArgumentException e) {
            lblError.setText(e.getMessage());
        } catch (SQLException | RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        hideForm();
        clearForm();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SET DEFAULT (quick action from table)
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onSetDefault() {
        DeliveryAddress sel = tblAddresses.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an address.");
            return;
        }
        if (sel.isDefault()) {
            showAlert(Alert.AlertType.INFORMATION, "Already Default",
                    "This address is already the default.");
            return;
        }
        try {
            service.setDefault(sel.getAddressId(), sel.getCustomerId());
            loadTable();
            showAlert(Alert.AlertType.INFORMATION, "Success",
                    "Default address updated for customer #" + sel.getCustomerId());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATS
    // ─────────────────────────────────────────────────────────────────────────

    private void loadStats() {
        try {
            List<Object[]> byCity = service.getOrdersByCity();
            if (!byCity.isEmpty()) {
                Object[] top = byCity.get(0);
                lblTopCity.setText("Top City: " + top[0] + " (" + top[1] + " orders)");
            } else {
                lblTopCity.setText("Top City: N/A");
            }

            List<Object[]> usage = service.getDefaultUsageRate();
            long defaultCount = 0, nonDefaultCount = 0;
            for (Object[] row : usage) {
                if ("Default".equals(row[0]))    defaultCount    = ((Number) row[1]).longValue();
                if ("Non-Default".equals(row[0])) nonDefaultCount = ((Number) row[1]).longValue();
            }
            long total = defaultCount + nonDefaultCount;
            if (total > 0) {
                long pct = defaultCount * 100 / total;
                lblDefaultRate.setText("Default address used: " + pct + "% of orders");
            } else {
                lblDefaultRate.setText("Default address used: N/A");
            }
        } catch (SQLException | RuntimeException e) {
            lblTopCity.setText("Stats unavailable");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FORM HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void setupFormComboBoxes() {
        cmbStatus.setItems(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        // CustomerDAO.findAll() wraps SQLException in RuntimeException — catch accordingly
        try {
            List<Customer> customers = customerDao.findAll();
            cmbCustomer.setItems(FXCollections.observableArrayList(customers));
            cmbCustomer.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(Customer c) {
                    return c == null ? "" : c.getCustomerId() + " – " + c.getFullName();
                }
                @Override public Customer fromString(String s) { return null; }
            });
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Load Error",
                    "Failed to load customers: " + e.getMessage());
        }
    }

    private void populateForm(DeliveryAddress a) {
        // Select matching customer in combo
        if (cmbCustomer.getItems() != null) {
            cmbCustomer.getItems().stream()
                    .filter(c -> c.getCustomerId() == a.getCustomerId())
                    .findFirst().ifPresent(cmbCustomer::setValue);
        }
        txtReceiverName.setText(a.getReceiverName());
        txtPhone.setText(a.getPhone());
        txtAddressLine.setText(a.getAddressLine());
        txtWard.setText(a.getWard());
        txtDistrict.setText(a.getDistrict());
        txtCity.setText(a.getCity());
        chkDefault.setSelected(a.isDefault());
        cmbStatus.setValue(a.getStatus());
    }

    private DeliveryAddress buildFromForm() {
        Customer cust = cmbCustomer.getValue();
        if (cust == null) throw new IllegalArgumentException("Customer is required.");

        DeliveryAddress addr = new DeliveryAddress();
        addr.setCustomerId(cust.getCustomerId());
        addr.setReceiverName(txtReceiverName.getText().trim());
        addr.setPhone(txtPhone.getText().trim());
        addr.setAddressLine(txtAddressLine.getText().trim());
        addr.setWard(txtWard.getText().trim());
        addr.setDistrict(txtDistrict.getText().trim());
        addr.setCity(txtCity.getText().trim());
        addr.setDefault(chkDefault.isSelected());
        addr.setStatus(cmbStatus.getValue() != null ? cmbStatus.getValue() : "ACTIVE");
        return addr;
    }

    private void clearForm() {
        cmbCustomer.setValue(null);
        txtReceiverName.clear();
        txtPhone.clear();
        txtAddressLine.clear();
        txtWard.clear();
        txtDistrict.clear();
        txtCity.clear();
        chkDefault.setSelected(false);
        cmbStatus.setValue("ACTIVE");
        lblError.setText("");
    }

    private void showForm()  { formPanel.setVisible(true);  formPanel.setManaged(true); }
    private void hideForm()  { formPanel.setVisible(false); formPanel.setManaged(false); }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}