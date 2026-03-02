package furniture_system.controller;

import furniture_system.model.Account;
import furniture_system.model.Account.Role;
import furniture_system.model.Account.Status;
import furniture_system.service.AuthService;
import javafx.beans.property.SimpleIntegerProperty;
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

import java.time.format.DateTimeFormatter;
import java.util.List;

public class AuthController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── FXML bindings ─────────────────────────────────────────────────────────
    @FXML private TableView<Account>            accountTable;
    @FXML private TableColumn<Account,Integer>  colId;
    @FXML private TableColumn<Account,String>   colUsername;
    @FXML private TableColumn<Account,String>   colRole;
    @FXML private TableColumn<Account,String>   colStatus;
    @FXML private TableColumn<Account,String>   colCreatedAt;
    @FXML private TableColumn<Account,String>   colLastLogin;
    @FXML private TableColumn<Account,Integer>  colFailed;

    @FXML private ComboBox<Status>  statusCombo;
    @FXML private ComboBox<Role>    roleCombo;
    @FXML private Button            btnUpdateStatus;
    @FXML private Button            btnUpdateRole;
    @FXML private Button            btnReset;
    @FXML private Button            btnRefresh;
    @FXML private Button            btnAdd;
    @FXML private Button            btnEdit;
    @FXML private Button            btnDelete;
    @FXML private Label             statusBarLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private final AuthService             authService = new AuthService();
    private final ObservableList<Account> data        = FXCollections.observableArrayList();

    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        setupColumns();
        setupCombos();
        loadAccounts();

        accountTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> {
                    boolean has = selected != null;
                    btnUpdateStatus.setDisable(!has);
                    btnUpdateRole.setDisable(!has);
                    btnReset.setDisable(!has);
                    btnEdit.setDisable(!has);
                    btnDelete.setDisable(!has);
                });

        btnUpdateStatus.setDisable(true);
        btnUpdateRole.setDisable(true);
        btnReset.setDisable(true);
        btnEdit.setDisable(true);
        btnDelete.setDisable(true);
    }

    // ── Columns ───────────────────────────────────────────────────────────────
    private void setupColumns() {
        colId.setCellValueFactory(c ->
            new SimpleIntegerProperty(c.getValue().getAccountId()).asObject());
        colUsername.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getUsername()));
        colRole.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getRole().name()));
        colStatus.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getStatus().name()));
        colCreatedAt.setCellValueFactory(c -> {
            var dt = c.getValue().getCreatedAt();
            return new SimpleStringProperty(dt != null ? dt.format(FMT) : "—");
        });
        colLastLogin.setCellValueFactory(c -> {
            var dt = c.getValue().getLastLoginAt();
            return new SimpleStringProperty(dt != null ? dt.format(FMT) : "Never");
        });
        colFailed.setCellValueFactory(c ->
            new SimpleIntegerProperty(c.getValue().getFailedAttempts()).asObject());

        // Row colouring
        accountTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                switch (item.getStatus()) {
                    case SUSPENDED -> setStyle("-fx-background-color:#ffe0e0;");
                    case INACTIVE  -> setStyle("-fx-background-color:#e8e8e8;");
                    default        -> setStyle("");
                }
            }
        });
        accountTable.setItems(data);
    }

    private void setupCombos() {
        statusCombo.setItems(FXCollections.observableArrayList(Status.values()));
        statusCombo.getSelectionModel().selectFirst();
        roleCombo.setItems(FXCollections.observableArrayList(Role.values()));
        roleCombo.getSelectionModel().selectFirst();
    }

    // ── Load ──────────────────────────────────────────────────────────────────
    private void loadAccounts() {
        try {
            List<Account> accounts = authService.getAllAccounts();
            data.setAll(accounts);
            setStatusBar("Loaded " + accounts.size() + " account(s).");
        } catch (SecurityException e) {
            showAlert(Alert.AlertType.ERROR, "Access Denied", e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load accounts: " + e.getMessage());
        }
    }

    // ── Existing handlers (unchanged) ─────────────────────────────────────────

    @FXML public void handleRefresh() { loadAccounts(); }

    @FXML
    public void handleUpdateStatus() {
        Account sel = accountTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Status newStatus = statusCombo.getValue();
        if (newStatus == null) { showAlert(Alert.AlertType.WARNING, "No Status", "Please select a status."); return; }
        try {
            if (authService.updateAccountStatus(sel.getAccountId(), newStatus)) {
                setStatusBar("Status updated to " + newStatus + " for [" + sel.getUsername() + "].");
                loadAccounts();
            }
        } catch (Exception e) { showAlert(Alert.AlertType.ERROR, "Update Failed", e.getMessage()); }
    }

    @FXML
    public void handleUpdateRole() {
        Account sel = accountTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Role newRole = roleCombo.getValue();
        if (newRole == null) { showAlert(Alert.AlertType.WARNING, "No Role", "Please select a role."); return; }
        try {
            if (authService.updateAccountRole(sel.getAccountId(), newRole)) {
                setStatusBar("Role updated to " + newRole + " for [" + sel.getUsername() + "].");
                loadAccounts();
            }
        } catch (IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Not Allowed", e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Update Failed", e.getMessage());
        }
    }

    @FXML
    public void handleResetFailedAttempts() {
        Account sel = accountTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Reset failed attempts and re-activate account [" + sel.getUsername() + "]?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Reset"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                if (authService.resetFailedAttempts(sel.getAccountId())) {
                    setStatusBar("Account [" + sel.getUsername() + "] unlocked and re-activated.");
                    loadAccounts();
                }
            } catch (Exception e) { showAlert(Alert.AlertType.ERROR, "Reset Failed", e.getMessage()); }
        });
    }

    // ── EDIT (Rename / Change Password) ──────────────────────────────────────
    @FXML
    public void handleEdit() {
        Account sel = accountTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Account – " + sel.getUsername());

        TextField     tfUsername  = new TextField(sel.getUsername());
        PasswordField pfNewPass   = new PasswordField();
        PasswordField pfConfirm   = new PasswordField();
        Label         lblErr      = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;");
        lblErr.setWrapText(true);

        Label hintPass = new Label("Leave blank to keep current password. Min 6 chars if changing.");
        hintPass.setStyle("-fx-font-size:10px;-fx-text-fill:#888;");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(24));
        grid.getColumnConstraints().addAll(
            new ColumnConstraints(130), new ColumnConstraints(240));

        int row = 0;
        // Info row
        Label lblInfo = new Label("Account ID: " + sel.getAccountId());
        lblInfo.setStyle("-fx-text-fill:#555;-fx-font-size:12px;");
        grid.add(lblInfo, 0, row, 2, 1); row++;

        grid.add(new Separator(), 0, row, 2, 1); row++;

        grid.add(fieldLabel("New Username *"), 0, row); grid.add(tfUsername,  1, row++);
        grid.add(fieldLabel("New Password"),   0, row); grid.add(pfNewPass,   1, row++);
        grid.add(fieldLabel("Confirm"),        0, row); grid.add(pfConfirm,   1, row++);
        grid.add(new Label(""),                0, row); grid.add(hintPass,    1, row++);
        grid.add(lblErr,                       0, row, 2, 1); row++;

        Button btnSave   = new Button("💾  Save Changes");
        Button btnCancel = new Button("Cancel");
        btnSave.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;" +
            "-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave);
        btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            boolean changed = false;
            try {
                // ── Update username if changed ─────────────────────────────
                String newUser = tfUsername.getText().trim();
                if (!newUser.equals(sel.getUsername())) {
                    authService.updateUsername(sel.getAccountId(), newUser);
                    changed = true;
                }

                // ── Update password if provided ────────────────────────────
                String pw = pfNewPass.getText();
                String cf = pfConfirm.getText();
                if (!pw.isEmpty()) {
                    if (!pw.equals(cf)) {
                        lblErr.setText("⚠  Passwords do not match.");
                        return;
                    }
                    authService.updatePassword(sel.getAccountId(), pw);
                    changed = true;
                }

                if (!changed) {
                    lblErr.setText("⚠  No changes detected.");
                    return;
                }

                setStatusBar("Account [" + sel.getUsername() + "] updated successfully.");
                loadAccounts();
                dlg.close();
            } catch (IllegalArgumentException ex) {
                lblErr.setText("⚠  " + ex.getMessage());
            } catch (Exception ex) {
                lblErr.setText("Error: " + ex.getMessage());
            }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid));
        dlg.setResizable(false);
        dlg.showAndWait();
    }

    // ── ADD ───────────────────────────────────────────────────────────────────
    @FXML
    public void handleAdd() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Add New Account");

        TextField        tfUsername = new TextField();
        PasswordField    pfPassword = new PasswordField();
        PasswordField    pfConfirm  = new PasswordField();
        ComboBox<Role>   cbRole     = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        ComboBox<Status> cbStatus   = new ComboBox<>(FXCollections.observableArrayList(Status.values()));
        Label            lblErr     = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;");
        lblErr.setWrapText(true);

        cbRole.getSelectionModel().select(Role.EMPLOYEE);
        cbStatus.getSelectionModel().select(Status.ACTIVE);

        // ── Layout ────────────────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(24));
        grid.getColumnConstraints().addAll(
            new ColumnConstraints(110), new ColumnConstraints(240));

        int row = 0;
        grid.add(fieldLabel("Username *"),  0, row); grid.add(tfUsername, 1, row++);
        grid.add(fieldLabel("Password *"),  0, row); grid.add(pfPassword, 1, row++);
        grid.add(fieldLabel("Confirm *"),   0, row); grid.add(pfConfirm,  1, row++);
        grid.add(fieldLabel("Role *"),      0, row); grid.add(cbRole,     1, row++);
        grid.add(fieldLabel("Status *"),    0, row); grid.add(cbStatus,   1, row++);
        grid.add(lblErr,                    0, row, 2, 1); row++;

        // Hint labels
        Label hintUser = new Label("≥ 3 chars, letters/digits/underscore only");
        hintUser.setStyle("-fx-font-size:10px;-fx-text-fill:#888;");
        Label hintPass = new Label("≥ 6 characters");
        hintPass.setStyle("-fx-font-size:10px;-fx-text-fill:#888;");
        grid.add(hintUser, 1, 0);  // overlapping — use separate rows
        // Re-do layout with hints
        grid.getChildren().clear();
        row = 0;
        grid.add(fieldLabel("Username *"),  0, row); grid.add(tfUsername, 1, row++);
        grid.add(new Label(""),             0, row);
        grid.add(hintUser,                  1, row++);
        grid.add(fieldLabel("Password *"),  0, row); grid.add(pfPassword, 1, row++);
        grid.add(new Label(""),             0, row);
        grid.add(hintPass,                  1, row++);
        grid.add(fieldLabel("Confirm *"),   0, row); grid.add(pfConfirm,  1, row++);
        grid.add(fieldLabel("Role *"),      0, row); grid.add(cbRole,     1, row++);
        grid.add(fieldLabel("Status *"),    0, row); grid.add(cbStatus,   1, row++);
        grid.add(lblErr,                    0, row, 2, 1); row++;

        cbRole.setMaxWidth(Double.MAX_VALUE);
        cbStatus.setMaxWidth(Double.MAX_VALUE);

        Button btnSave   = new Button("➕  Add Account");
        Button btnCancel = new Button("Cancel");
        btnSave.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;" +
            "-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave);
        btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        // ── Save action ───────────────────────────────────────────────────────
        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            String pw = pfPassword.getText();
            String cf = pfConfirm.getText();
            if (!pw.equals(cf)) {
                lblErr.setText("⚠  Passwords do not match.");
                return;
            }
            try {
                int newId = authService.addAccount(
                    tfUsername.getText(), pw, cbRole.getValue(), cbStatus.getValue());
                setStatusBar("Account added — ID: " + newId + ", Username: " + tfUsername.getText().trim());
                loadAccounts();
                dlg.close();
            } catch (IllegalArgumentException ex) {
                lblErr.setText("⚠  " + ex.getMessage());
            } catch (Exception ex) {
                lblErr.setText("Error: " + ex.getMessage());
            }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid));
        dlg.setResizable(false);
        dlg.showAndWait();
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    @FXML
    public void handleDelete() {
        Account sel = accountTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Permanently delete account [" + sel.getUsername() + "] (ID: " + sel.getAccountId() + ")?\n\n" +
            "⚠ This action cannot be undone.\n" +
            "⚠ Not allowed if linked to an Employee record.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                authService.deleteAccount(sel.getAccountId());
                setStatusBar("Account [" + sel.getUsername() + "] permanently deleted.");
                loadAccounts();
            } catch (IllegalStateException ex) {
                showAlert(Alert.AlertType.WARNING, "Cannot Delete", ex.getMessage());
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Delete Failed", ex.getMessage());
            }
        });
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    private Label fieldLabel(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;");
        return l;
    }
    private void setStatusBar(String msg) { if (statusBarLabel != null) statusBarLabel.setText(msg); }
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(content); alert.showAndWait();
    }
}