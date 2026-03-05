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

    @FXML private TableView<Account>           accountTable;
    @FXML private TableColumn<Account,Integer> colId;
    @FXML private TableColumn<Account,String>  colUsername;
    @FXML private TableColumn<Account,String>  colEmail;
    @FXML private TableColumn<Account,String>  colRole;
    @FXML private TableColumn<Account,String>  colStatus;
    @FXML private TableColumn<Account,String>  colLastLogin;
    @FXML private TableColumn<Account,Integer> colFailed;
    @FXML private Button btnRefresh, btnAdd, btnEdit, btnDelete;
    @FXML private Label  statusBarLabel;

    private final AuthService             authService = new AuthService();
    private final ObservableList<Account> data        = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupColumns();
        loadAccounts();
        accountTable.getSelectionModel().selectedItemProperty().addListener((obs,old,sel) -> {
            boolean has = sel != null;
            btnEdit.setDisable(!has); btnDelete.setDisable(!has);
        });
        btnEdit.setDisable(true); btnDelete.setDisable(true);
    }

    private void setupColumns() {
        colId.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getAccountId()).asObject());
        colUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        colEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail() != null ? c.getValue().getEmail() : "-"));
        colRole.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRole().name()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        colLastLogin.setCellValueFactory(c -> {
            var dt = c.getValue().getLastLoginAt();
            return new SimpleStringProperty(dt != null ? dt.format(FMT) : "Never");
        });
        colFailed.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getFailedAttempts()).asObject());
        accountTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty); if (empty || item == null) { setStyle(""); return; }
                switch (item.getStatus()) {
                    case SUSPENDED -> setStyle("-fx-background-color:#ffe0e0;");
                    case INACTIVE  -> setStyle("-fx-background-color:#e8e8e8;");
                    default        -> setStyle("");
                }
            }
        });
        accountTable.setItems(data);
    }

    private void loadAccounts() {
        try {
            List<Account> accounts = authService.getAllAccounts();
            data.setAll(accounts);
            setStatus("Loaded " + accounts.size() + " account(s).");
        } catch (SecurityException e) { alert(Alert.AlertType.ERROR,"Access Denied",e.getMessage()); }
          catch (Exception e)         { alert(Alert.AlertType.ERROR,"Error","Failed: "+e.getMessage()); }
    }

    @FXML public void handleRefresh() { loadAccounts(); }

    // ==================== EDIT ====================
    @FXML
    public void handleEdit() {
        Account sel = accountTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Account - " + sel.getUsername());
        TextField        tfUsername = new TextField(sel.getUsername());
        TextField        tfEmail    = new TextField(sel.getEmail() != null ? sel.getEmail() : "");
        PasswordField    pfPass     = new PasswordField();
        PasswordField    pfConfirm  = new PasswordField();
        ComboBox<Status> cbStatus   = new ComboBox<>(FXCollections.observableArrayList(Status.values()));
        ComboBox<Role>   cbRole     = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        CheckBox         chkUnlock  = new CheckBox("Reset failed attempts and re-activate account");
        cbStatus.setValue(sel.getStatus()); cbStatus.setMaxWidth(Double.MAX_VALUE);
        cbRole.setValue(sel.getRole());     cbRole.setMaxWidth(Double.MAX_VALUE);
        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(24));
        grid.getColumnConstraints().addAll(new ColumnConstraints(145), new ColumnConstraints(260));
        int row = 0;
        Label lblInfo = new Label("Account ID: " + sel.getAccountId());
        lblInfo.setStyle("-fx-text-fill:#555;-fx-font-size:12px;");
        grid.add(lblInfo, 0, row, 2, 1); row++;
        grid.add(new Separator(), 0, row, 2, 1); row++;
        grid.add(sec("-- Identity"), 0, row, 2, 1); row++;
        grid.add(fl("New Username *"), 0, row); grid.add(tfUsername, 1, row++);
        grid.add(fl("Email"),          0, row); grid.add(tfEmail,    1, row++);
        grid.add(new Label(""), 0, row); grid.add(hint("Optional. Used for password reset via Gmail."), 1, row++);
        grid.add(new Separator(), 0, row, 2, 1); row++;
        grid.add(sec("-- Password"), 0, row, 2, 1); row++;
        grid.add(fl("New Password"), 0, row); grid.add(pfPass,    1, row++);
        grid.add(fl("Confirm"),      0, row); grid.add(pfConfirm, 1, row++);
        grid.add(new Label(""), 0, row); grid.add(hint("Leave blank to keep current. Min 6 chars."), 1, row++);
        grid.add(new Separator(), 0, row, 2, 1); row++;
        grid.add(sec("-- Access Control"), 0, row, 2, 1); row++;
        grid.add(fl("Status"), 0, row); grid.add(cbStatus, 1, row++);
        grid.add(fl("Role"),   0, row); grid.add(cbRole,   1, row++);
        grid.add(new Separator(), 0, row, 2, 1); row++;
        grid.add(sec("-- Unlock"), 0, row, 2, 1); row++;
        grid.add(new Label(""), 0, row); grid.add(chkUnlock, 1, row++);
        grid.add(new Label(""), 0, row); grid.add(hint("Resets FailedAttempts to 0, forces Status to ACTIVE."), 1, row++);
        chkUnlock.selectedProperty().addListener((obs,was,now) -> {
            if (now) cbStatus.setValue(Status.ACTIVE); cbStatus.setDisable(now);
        });
        grid.add(new Separator(), 0, row, 2, 1); row++;
        grid.add(lblErr, 0, row, 2, 1); row++;
        Button btnSave = primaryBtn("Save Changes"); Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);
        btnSave.setOnAction(ev -> {
            lblErr.setText(""); boolean changed = false;
            try {
                String nu = tfUsername.getText().trim();
                if (!nu.equals(sel.getUsername())) { authService.updateUsername(sel.getAccountId(), nu); changed = true; }
                String ne = tfEmail.getText().trim();
                String ce = sel.getEmail() != null ? sel.getEmail() : "";
                if (!ne.equalsIgnoreCase(ce)) { authService.updateEmail(sel.getAccountId(), ne.isEmpty() ? null : ne); changed = true; }
                String pw = pfPass.getText(); String cf = pfConfirm.getText();
                if (!pw.isEmpty()) { if (!pw.equals(cf)) { lblErr.setText("Passwords do not match."); return; }
                    authService.updatePassword(sel.getAccountId(), pw); changed = true; }
                if (cbStatus.getValue() != sel.getStatus()) { authService.updateAccountStatus(sel.getAccountId(), cbStatus.getValue()); changed = true; }
                if (cbRole.getValue() != sel.getRole())     { authService.updateAccountRole(sel.getAccountId(),   cbRole.getValue());   changed = true; }
                if (chkUnlock.isSelected())                 { authService.resetFailedAttempts(sel.getAccountId()); changed = true; }
                if (!changed) { lblErr.setText("No changes detected."); return; }
                setStatus("Account [" + sel.getUsername() + "] updated."); loadAccounts(); dlg.close();
            } catch (IllegalArgumentException | IllegalStateException ex) { lblErr.setText(ex.getMessage()); }
              catch (Exception ex) { lblErr.setText("Error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());
        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 460, 660)); dlg.showAndWait();
    }

    // ==================== ADD ====================
    @FXML
    public void handleAdd() {
        Stage dlg = new Stage(); dlg.initModality(Modality.APPLICATION_MODAL); dlg.setTitle("Add New Account");
        TextField tfUsername = new TextField(); TextField tfEmail = new TextField();
        PasswordField pfPassword = new PasswordField(); PasswordField pfConfirm = new PasswordField();
        ComboBox<Role> cbRole = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        ComboBox<Status> cbStatus = new ComboBox<>(FXCollections.observableArrayList(Status.values()));
        cbRole.getSelectionModel().select(Role.EMPLOYEE); cbStatus.getSelectionModel().select(Status.ACTIVE);
        cbRole.setMaxWidth(Double.MAX_VALUE); cbStatus.setMaxWidth(Double.MAX_VALUE);
        Label lblErr = new Label(); lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(24));
        grid.getColumnConstraints().addAll(new ColumnConstraints(120), new ColumnConstraints(260));
        int row = 0;
        grid.add(fl("Username *"), 0, row); grid.add(tfUsername, 1, row++);
        grid.add(new Label(""), 0, row);    grid.add(hint("3-50 chars, letters/digits/underscore only"), 1, row++);
        grid.add(fl("Password *"), 0, row); grid.add(pfPassword, 1, row++);
        grid.add(new Label(""), 0, row);    grid.add(hint("Min 6 characters"), 1, row++);
        grid.add(fl("Confirm *"),  0, row); grid.add(pfConfirm,  1, row++);
        grid.add(fl("Email"),      0, row); grid.add(tfEmail,    1, row++);
        grid.add(new Label(""), 0, row);    grid.add(hint("Optional - used for password reset"), 1, row++);
        grid.add(fl("Role *"),     0, row); grid.add(cbRole,     1, row++);
        grid.add(fl("Status *"),   0, row); grid.add(cbStatus,   1, row++);
        grid.add(lblErr, 0, row, 2, 1); row++;
        Button btnSave = primaryBtn("Add Account"); Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);
        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            if (!pfPassword.getText().equals(pfConfirm.getText())) { lblErr.setText("Passwords do not match."); return; }
            try {
                String email = tfEmail.getText().trim();
                int id = authService.addAccount(tfUsername.getText(), pfPassword.getText(),
                    cbRole.getValue(), cbStatus.getValue(), email.isEmpty() ? null : email);
                setStatus("Account added - ID: " + id); loadAccounts(); dlg.close();
            } catch (Exception ex) { lblErr.setText(ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());
        dlg.setScene(new Scene(grid)); dlg.setResizable(false); dlg.showAndWait();
    }

    // ==================== DELETE ====================
    @FXML
    public void handleDelete() {
        Account sel = accountTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Permanently delete [" + sel.getUsername() + "] (ID: " + sel.getAccountId() + ")?\n\n" +
            "Cannot undo. Not allowed if linked to an Employee.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try { authService.deleteAccount(sel.getAccountId()); setStatus("Deleted [" + sel.getUsername() + "]."); loadAccounts(); }
            catch (IllegalStateException ex) { alert(Alert.AlertType.WARNING, "Cannot Delete", ex.getMessage()); }
            catch (Exception ex)             { alert(Alert.AlertType.ERROR,   "Delete Failed",  ex.getMessage()); }
        });
    }

    private Label fl(String t)   { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
    private Label hint(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:10px;-fx-text-fill:#888;"); return l; }
    private Button primaryBtn(String t) { Button b = new Button(t); b.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;"); return b; }
    private void setStatus(String msg) { if (statusBarLabel != null) statusBarLabel.setText(msg); }
    private void alert(Alert.AlertType t, String title, String msg) { Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait(); }
}