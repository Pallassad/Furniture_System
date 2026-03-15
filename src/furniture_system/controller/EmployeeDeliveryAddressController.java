package furniture_system.controller;

import furniture_system.model.DeliveryAddress;
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
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * EmployeeDeliveryAddressController
 *
 * Used during order creation:
 *  1. View all ACTIVE addresses for a given customer.
 *  2. Add a new address for that customer.
 *  3. Select an address → fires onAddressSelected callback so the
 *     EmployeeOrderController can set Order.AddressId.
 *
 * Usage from EmployeeOrderController:
 * <pre>
 *   employeeDeliveryCtrl.loadForCustomer(customerId);
 *   employeeDeliveryCtrl.setOnAddressSelected(addr -> {
 *       currentOrder.setAddressId(addr.getAddressId());
 *   });
 * </pre>
 */
public class EmployeeDeliveryAddressController implements Initializable {

    // ── Table ────────────────────────────────────────────────────────────────
    @FXML private TableView<DeliveryAddress>            tblAddresses;
    @FXML private TableColumn<DeliveryAddress, Integer> colId;
    @FXML private TableColumn<DeliveryAddress, String>  colReceiver;
    @FXML private TableColumn<DeliveryAddress, String>  colPhone;
    @FXML private TableColumn<DeliveryAddress, String>  colAddress;
    @FXML private TableColumn<DeliveryAddress, String>  colWard;
    @FXML private TableColumn<DeliveryAddress, String>  colDistrict;
    @FXML private TableColumn<DeliveryAddress, String>  colCity;
    @FXML private TableColumn<DeliveryAddress, Boolean> colDefault;

    // ── Add-address form ─────────────────────────────────────────────────────
    @FXML private VBox      addFormPanel;
    @FXML private TextField txtReceiverName;
    @FXML private TextField txtPhone;
    @FXML private TextField txtAddressLine;
    @FXML private TextField txtWard;
    @FXML private TextField txtDistrict;
    @FXML private TextField txtCity;
    @FXML private CheckBox  chkDefault;
    @FXML private Label     lblError;

    // ── Action buttons ───────────────────────────────────────────────────────
    @FXML private Button btnSelect;
    @FXML private Label  lblSelectedAddress;

    // ── State ────────────────────────────────────────────────────────────────
    private final DeliveryAddressService service = new DeliveryAddressService();
    private final ObservableList<DeliveryAddress> data = FXCollections.observableArrayList();

    private int currentCustomerId = -1;
    private Consumer<DeliveryAddress> onAddressSelected;  // callback → EmployeeOrderController

    // ─────────────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        tblAddresses.setItems(data);

        // Enable Select button only when a row is chosen
        btnSelect.setDisable(true);
        tblAddresses.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> btnSelect.setDisable(sel == null));

        hideAddForm();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API — called by EmployeeOrderController
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Load all ACTIVE addresses for the given customer.
     * Call this every time the customer changes in the order form.
     */
    public void loadForCustomer(int customerId) {
        this.currentCustomerId = customerId;
        lblSelectedAddress.setText("No address selected");
        refreshTable();
    }

    /**
     * Register a callback that fires when the employee clicks "Select".
     * The selected DeliveryAddress is passed to the callback.
     */
    public void setOnAddressSelected(Consumer<DeliveryAddress> callback) {
        this.onAddressSelected = callback;
    }

    /**
     * Returns the currently highlighted address in the table (may be null).
     */
    public DeliveryAddress getSelectedAddress() {
        return tblAddresses.getSelectionModel().getSelectedItem();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TABLE ACTIONS
    // ─────────────────────────────────────────────────────────────────────────

    /** Employee clicks "Select" → fires callback to link address to order. */
    @FXML
    private void onSelect() {
        DeliveryAddress sel = tblAddresses.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        lblSelectedAddress.setText("✔ " + sel.getFullAddress());
        if (onAddressSelected != null) onAddressSelected.accept(sel);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADD NEW ADDRESS
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onShowAddForm() {
        if (currentCustomerId <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Customer",
                    "Please select a customer before adding an address.");
            return;
        }
        clearAddForm();
        showAddForm();
    }

    @FXML
    private void onSaveNewAddress() {
        lblError.setText("");
        try {
            DeliveryAddress addr = buildFromForm();
            service.addAddress(addr);
            hideAddForm();
            clearAddForm();
            refreshTable();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Address added successfully.");
        } catch (IllegalArgumentException e) {
            lblError.setText(e.getMessage());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", e.getMessage());
        }
    }

    @FXML
    private void onCancelAdd() {
        hideAddForm();
        clearAddForm();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void setupColumns() {
        colId.setCellValueFactory(c ->
                new SimpleIntegerProperty(c.getValue().getAddressId()).asObject());
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

        // Highlight the default row with a subtle background
        tblAddresses.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(DeliveryAddress item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if (item.isDefault()) {
                    setStyle("-fx-background-color: #eafaf1;"); // soft green for default
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void refreshTable() {
        if (currentCustomerId <= 0) { data.clear(); return; }
        try {
            data.setAll(service.getByCustomerId(currentCustomerId));
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Load Error", e.getMessage());
        }
    }

    private DeliveryAddress buildFromForm() {
        DeliveryAddress addr = new DeliveryAddress();
        addr.setCustomerId(currentCustomerId);
        addr.setReceiverName(txtReceiverName.getText().trim());
        addr.setPhone(txtPhone.getText().trim());
        addr.setAddressLine(txtAddressLine.getText().trim());
        addr.setWard(txtWard.getText().trim());
        addr.setDistrict(txtDistrict.getText().trim());
        addr.setCity(txtCity.getText().trim());
        addr.setDefault(chkDefault.isSelected());
        return addr;
    }

    private void clearAddForm() {
        txtReceiverName.clear();
        txtPhone.clear();
        txtAddressLine.clear();
        txtWard.clear();
        txtDistrict.clear();
        txtCity.clear();
        chkDefault.setSelected(false);
        lblError.setText("");
    }

    private void showAddForm()  { addFormPanel.setVisible(true);  addFormPanel.setManaged(true); }
    private void hideAddForm()  { addFormPanel.setVisible(false); addFormPanel.setManaged(false); }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}