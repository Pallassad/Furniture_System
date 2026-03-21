package furniture_system.controller;

import furniture_system.dao.AccountDAO;
import furniture_system.model.Account;
import furniture_system.model.Employee;
import furniture_system.model.Employee.Position;
import furniture_system.model.Employee.Status;
import furniture_system.service.EmployeeService;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * EmployeeController – backs employee_management.fxml
 * Handles: View, Add, Update, Delete/Deactivate, Search, Statistics
 */
public class EmployeeController {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter D_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── Table ─────────────────────────────────────────────────────────────────
    @FXML private TableView<Employee>            employeeTable;
    @FXML private TableColumn<Employee,Integer>  colId;
    @FXML private TableColumn<Employee,String>   colFullName;
    @FXML private TableColumn<Employee,String>   colPhone;
    @FXML private TableColumn<Employee,String>   colEmail;
    @FXML private TableColumn<Employee,String>   colPosition;
    @FXML private TableColumn<Employee,String>   colStatus;
    @FXML private TableColumn<Employee,String>   colDob;
    @FXML private TableColumn<Employee,String>   colAccount;
    @FXML private TableColumn<Employee,String>   colHiredAt;

    // ── Search bar ────────────────────────────────────────────────────────────
    @FXML private TextField searchField;

    // ── Toolbar buttons ───────────────────────────────────────────────────────
    @FXML private Button btnAdd;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnRefresh;
    @FXML private Button btnStats;

    // ── Status bar ────────────────────────────────────────────────────────────
    @FXML private Label statusBarLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private final EmployeeService            service = new EmployeeService();
    private final ObservableList<Employee>   data    = FXCollections.observableArrayList();

    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        setupColumns();
        loadAll();

        // Enable Edit/Delete only when row selected
        employeeTable.getSelectionModel().selectedItemProperty().addListener(
                (o, old, sel) -> {
                    btnEdit.setDisable(sel == null);
                    btnDelete.setDisable(sel == null);
                });
        btnEdit.setDisable(true);
        btnDelete.setDisable(true);

        // Live search on every keystroke
        searchField.textProperty().addListener((o, old, val) -> handleSearch());
    }

    // ── Column setup ──────────────────────────────────────────────────────────
    private void setupColumns() {
        colId.setCellValueFactory(c ->
                new SimpleIntegerProperty(c.getValue().getEmployeeId()).asObject());
        colFullName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getFullName()));
        colPhone.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPhone()));
        colEmail.setCellValueFactory(c ->
                new SimpleStringProperty(nvl(c.getValue().getEmail())));
        colPosition.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPosition().name()));
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus().name()));
        colDob.setCellValueFactory(c -> {
            var d = c.getValue().getDob();
            return new SimpleStringProperty(d != null ? d.format(D_FMT) : "—");
        });
        colAccount.setCellValueFactory(c ->
                new SimpleStringProperty(nvl(c.getValue().getAccountUsername())));
        colHiredAt.setCellValueFactory(c -> {
            var dt = c.getValue().getHiredAt();
            return new SimpleStringProperty(dt != null ? dt.format(DT_FMT) : "—");
        });

        // Row colouring
        employeeTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Employee item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                switch (item.getStatus()) {
                    case SUSPENDED -> setStyle("-fx-background-color:#ffe0e0;");
                    case INACTIVE  -> setStyle("-fx-background-color:#e8e8e8;");
                    default        -> setStyle("");
                }
            }
        });
        employeeTable.setItems(data);
    }

    // ── Data load ─────────────────────────────────────────────────────────────
    private void loadAll() {
        try {
            data.setAll(service.getAll());
            setStatus("Loaded " + data.size() + " employee(s).");
        } catch (Exception e) { showError("Load failed", e.getMessage()); }
    }

    // ── 3.3.5 Search ──────────────────────────────────────────────────────────
    @FXML public void handleSearch() {
        try {
            String kw = searchField.getText();
            data.setAll(service.search(kw));
            setStatus("Found " + data.size() + " result(s) for: \"" + kw + "\"");
        } catch (Exception e) { showError("Search failed", e.getMessage()); }
    }

    @FXML public void handleRefresh() {
        searchField.clear();
        loadAll();
    }

    // ── 3.3.2 Add ─────────────────────────────────────────────────────────────
    @FXML public void handleAdd() {
        openForm(null);
    }

    // ── 3.3.3 Edit ────────────────────────────────────────────────────────────
    @FXML public void handleEdit() {
        Employee sel = employeeTable.getSelectionModel().getSelectedItem();
        if (sel != null) openForm(sel);
    }

    // ── 3.3.4 Delete / Deactivate ─────────────────────────────────────────────
    @FXML public void handleDelete() {
        Employee sel = employeeTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove employee [" + sel.getFullName() + "]?\n\n" +
                "• If they have Orders/Salary → Status will be set to INACTIVE.\n" +
                "• Otherwise the record will be permanently deleted.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Remove");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                String result = service.removeEmployee(sel.getEmployeeId());
                setStatus(result.equals("SOFT_DELETED")
                        ? "Employee set to INACTIVE (has related data)."
                        : "Employee permanently deleted.");
                loadAll();
            } catch (Exception e) { showError("Delete failed", e.getMessage()); }
        });
    }

    // ── 3.3.6 Statistics ──────────────────────────────────────────────────────
    @FXML public void handleStats() {
        Stage dialog = new Stage();
        dialog.setTitle("Employee Statistics");
        dialog.initModality(Modality.APPLICATION_MODAL);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        tabs.getTabs().add(buildPositionTab());
        tabs.getTabs().add(buildStatusTab());
        tabs.getTabs().add(buildMonthTab());

        Scene scene = new Scene(tabs, 600, 420);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ── Stats tabs ────────────────────────────────────────────────────────────
    private Tab buildPositionTab() {
        BarChart<String, Number> chart = new BarChart<>(
                new CategoryAxis(), new NumberAxis());
        chart.setTitle("Employees by Position");
        chart.setLegendVisible(false);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Object[] row : service.countByPosition())
            series.getData().add(new XYChart.Data<>((String) row[0], (Integer) row[1]));
        chart.getData().add(series);
        Tab tab = new Tab("By Position", chart);
        return tab;
    }

    private Tab buildStatusTab() {
        PieChart chart = new PieChart();
        chart.setTitle("Employees by Status");
        for (Object[] row : service.countByStatus())
            chart.getData().add(new PieChart.Data((String) row[0], (Integer) row[1]));
        return new Tab("By Status", chart);
    }

    private Tab buildMonthTab() {
        BarChart<String, Number> chart = new BarChart<>(
                new CategoryAxis(), new NumberAxis());
        chart.setTitle("New Employees per Month (last 12 months)");
        chart.setLegendVisible(false);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Object[] row : service.newEmployeesByMonth())
            series.getData().add(new XYChart.Data<>((String) row[0], (Integer) row[1]));
        chart.getData().add(series);
        return new Tab("New by Month", chart);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FORM DIALOG (Add / Edit)
    // ─────────────────────────────────────────────────────────────────────────
    private void openForm(Employee existing) {
        boolean isEdit = existing != null;

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isEdit ? "Edit Employee" : "Add New Employee");

        // ── Fields ────────────────────────────────────────────────────────────
        TextField      tfName     = new TextField();
        TextField      tfPhone    = new TextField();
        TextField      tfEmail    = new TextField();
        TextField      tfAddress  = new TextField();
        DatePicker     dpDob      = new DatePicker();
        ComboBox<Position> cbPos  = new ComboBox<>(
                FXCollections.observableArrayList(Position.values()));
        ComboBox<Status>   cbStat = new ComboBox<>(
                FXCollections.observableArrayList(Status.values()));
        ComboBox<Account>  cbAcc  = new ComboBox<>();
        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill:#c62828; -fx-font-size:12px;");
        lblError.setWrapText(true);

        // Load accounts for combo
        AccountDAO accountDAO = new AccountDAO();
        cbAcc.setItems(FXCollections.observableArrayList(accountDAO.findAll()));
        cbAcc.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Account a)   { return a == null ? "" : a.getAccountId() + " – " + a.getUsername(); }
            public Account fromString(String s) { return null; }
        });

        // Pre-fill when editing
        if (isEdit) {
            tfName.setText(existing.getFullName());
            tfPhone.setText(existing.getPhone());
            tfEmail.setText(nvl(existing.getEmail()));
            tfAddress.setText(nvl(existing.getAddress()));
            dpDob.setValue(existing.getDob());
            cbPos.setValue(existing.getPosition());
            cbStat.setValue(existing.getStatus());
            // select matching account
            cbAcc.getItems().stream()
                    .filter(a -> a.getAccountId() == existing.getAccountId())
                    .findFirst().ifPresent(cbAcc::setValue);
        } else {
            cbPos.getSelectionModel().selectFirst();
            cbStat.setValue(Status.ACTIVE);
        }

        // ── Layout ────────────────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(20));
        ColumnConstraints c1 = new ColumnConstraints(110);
        ColumnConstraints c2 = new ColumnConstraints(260);
        grid.getColumnConstraints().addAll(c1, c2);

        int row = 0;
        grid.add(label("Full Name *"),  0, row); grid.add(tfName,    1, row++);
        grid.add(label("Phone *"),      0, row); grid.add(tfPhone,   1, row++);
        grid.add(label("Email"),        0, row); grid.add(tfEmail,   1, row++);
        grid.add(label("Address"),      0, row); grid.add(tfAddress, 1, row++);
        grid.add(label("Date of Birth"),0, row); grid.add(dpDob,     1, row++);
        grid.add(label("Position *"),   0, row); grid.add(cbPos,     1, row++);
        grid.add(label("Status *"),     0, row); grid.add(cbStat,    1, row++);
        grid.add(label("Account *"),    0, row); grid.add(cbAcc,     1, row++);
        grid.add(lblError,              0, row, 2, 1); row++;

        Button btnSave   = new Button(isEdit ? "💾  Save Changes" : "➕  Add Employee");
        Button btnCancel = new Button("Cancel");
        btnSave.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;" +
                         "-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");

        HBox buttons = new HBox(10, btnCancel, btnSave);
        buttons.setStyle("-fx-alignment:CENTER_RIGHT;");
        grid.add(buttons, 0, row, 2, 1);

        // ── Save action ───────────────────────────────────────────────────────
        btnSave.setOnAction(ev -> {
            lblError.setText("");
            try {
                Employee e = isEdit ? existing : new Employee();
                e.setFullName(tfName.getText().trim());
                e.setPhone(tfPhone.getText().trim());
                e.setEmail(tfEmail.getText().trim());
                e.setAddress(tfAddress.getText().trim());
                e.setDob(dpDob.getValue());
                e.setPosition(cbPos.getValue());
                e.setStatus(cbStat.getValue());
                Account selAcc = cbAcc.getValue();
                if (selAcc == null) throw new IllegalArgumentException("Please select a linked account.");
                e.setAccountId(selAcc.getAccountId());

                if (isEdit) {
                    service.updateEmployee(e);
                    setStatus("Employee [" + e.getFullName() + "] updated.");
                } else {
                    int newId = service.addEmployee(e);
                    setStatus("Employee added with ID " + newId + ".");
                }
                loadAll();
                dialog.close();
            } catch (IllegalArgumentException ex) {
                lblError.setText("⚠ " + ex.getMessage());
            } catch (Exception ex) {
                lblError.setText("Error: " + ex.getMessage());
            }
        });
        btnCancel.setOnAction(ev -> dialog.close());

        cbPos.setMaxWidth(Double.MAX_VALUE);
        cbStat.setMaxWidth(Double.MAX_VALUE);
        cbAcc.setMaxWidth(Double.MAX_VALUE);

        dialog.setScene(new Scene(grid));
        dialog.setResizable(false);
        dialog.showAndWait();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    private Label label(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight:bold; -fx-font-size:12px;");
        return l;
    }
    private String nvl(String s) { return s == null ? "" : s; }
    private void setStatus(String msg) { if (statusBarLabel != null) statusBarLabel.setText(msg); }
    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
