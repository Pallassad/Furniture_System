package furniture_system.controller;

import furniture_system.dao.EmployeeDAO;
import furniture_system.model.Account;
import furniture_system.model.Employee;
import furniture_system.service.AuthService;
import furniture_system.service.LoginResult;
import furniture_system.utils.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class LoginController {

    @FXML private VBox          loginPane;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         errorLabel;

    @FXML private VBox      step1Pane;
    @FXML private TextField emailField;
    @FXML private Label     step1ErrorLabel;
    @FXML private Button    sendCodeButton;

    @FXML private VBox          step2Pane;
    @FXML private TextField     otpField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label         step2ErrorLabel;
    @FXML private Label         step2InfoLabel;
    @FXML private Label         passwordMatchLabel;
    @FXML private Button        resetButton;

    @FXML private VBox  step3Pane;
    @FXML private Label summaryUsername;
    @FXML private Label summaryTime;

    private final AuthService authService = new AuthService();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();  // ← THÊM MỚI

    @FXML
    public void initialize() {
        showPane(loginPane);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        passwordField.setOnAction(e -> handleLogin());
        confirmPasswordField.textProperty().addListener((obs, o, n) -> checkPasswordMatch());
        newPasswordField.textProperty().addListener((obs, o, n) -> checkPasswordMatch());
    }

    @FXML
    public void handleLogin() {
        loginButton.setDisable(true);
        clearError();
        LoginResult result = authService.login(
                usernameField.getText().trim(),
                passwordField.getText());
        if (result.isSuccess()) {
            onLoginSuccess(result.getAccount());
        } else {
            showError(result.getMessage());
            passwordField.clear();
            loginButton.setDisable(false);
        }
    }

    @FXML public void handleExit() { Platform.exit(); }

    @FXML
    public void handleForgotPassword() {
        emailField.clear();
        hideLabel(step1ErrorLabel);
        showPane(step1Pane);
    }

    @FXML
    public void handleSendCode() {
        hideLabel(step1ErrorLabel);
        String email = emailField.getText().trim();
        if (email.isEmpty()) { showLabel(step1ErrorLabel, "Please enter your email address."); return; }
        sendCodeButton.setDisable(true);
        sendCodeButton.setText("Sending...");
        try {
            if (authService.requestPasswordReset(email)) {
                otpField.clear(); newPasswordField.clear(); confirmPasswordField.clear();
                hideLabel(step2ErrorLabel); hideLabel(passwordMatchLabel);
                step2InfoLabel.setText("Check your inbox. Code expires in 15 minutes.");
                showPane(step2Pane);
            } else {
                showLabel(step1ErrorLabel, "No account found with that email address.");
            }
        } catch (Exception ex) {
            showLabel(step1ErrorLabel, ex.getMessage() != null ? ex.getMessage() : "Could not send email.");
        } finally {
            sendCodeButton.setDisable(false);
            sendCodeButton.setText("Send Code \u2192");
        }
    }

    @FXML public void handleBackToLogin() { clearError(); passwordField.clear(); showPane(loginPane); }

    @FXML
    public void handleResetPassword() {
        hideLabel(step2ErrorLabel);
        String otp = otpField.getText().trim();
        String newPass = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();
        if (otp.isEmpty())              { showLabel(step2ErrorLabel, "Please enter the reset code."); return; }
        if (newPass.isEmpty())          { showLabel(step2ErrorLabel, "Please enter a new password."); return; }
        if (!newPass.equals(confirm))   { showLabel(step2ErrorLabel, "Passwords do not match."); return; }
        resetButton.setDisable(true);
        resetButton.setText("Resetting...");
        try {
            authService.resetPasswordWithToken(otp, newPass, confirm);
            summaryUsername.setText(emailField.getText().trim());
            summaryTime.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            showPane(step3Pane);
        } catch (Exception ex) {
            showLabel(step2ErrorLabel, ex.getMessage() != null ? ex.getMessage() : "Reset failed.");
        } finally {
            resetButton.setDisable(false);
            resetButton.setText("Reset Password");
        }
    }

    @FXML public void handleBackToStep1() { hideLabel(step2ErrorLabel); hideLabel(passwordMatchLabel); showPane(step1Pane); }

    // ── HELPERS ──────────────────────────────────────────────────────────

    private void showPane(VBox target) {
        for (VBox pane : new VBox[]{loginPane, step1Pane, step2Pane, step3Pane}) {
            pane.setVisible(pane == target);
            pane.setManaged(pane == target);
        }
    }

    private void checkPasswordMatch() {
        String newPass = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();
        if (confirm.isEmpty()) { hideLabel(passwordMatchLabel); return; }
        if (newPass.equals(confirm)) {
            passwordMatchLabel.setText("\u2713 Passwords match");
            passwordMatchLabel.setStyle("-fx-text-fill:#43a047;-fx-font-size:11px;");
        } else {
            passwordMatchLabel.setText("\u2717 Passwords do not match");
            passwordMatchLabel.setStyle("-fx-text-fill:#c62828;-fx-font-size:11px;");
        }
        passwordMatchLabel.setVisible(true);
        passwordMatchLabel.setManaged(true);
    }

    private void showLabel(Label l, String msg) { l.setText(msg); l.setVisible(true); l.setManaged(true); }
    private void hideLabel(Label l)              { l.setText(""); l.setVisible(false); l.setManaged(false); }
    private void clearError()                    { hideLabel(errorLabel); }
    private void showError(String msg)           { showLabel(errorLabel, msg); }

    /**
     * Sau khi xác thực thành công:
     *  1. Lưu Account + Employee vào SessionManager   ← FIX CHÍNH
     *  2. Navigate sang dashboard tương ứng
     */
    private void onLoginSuccess(Account account) {
        try {
            // ── FIX: Lưu session TRƯỚC khi mở dashboard ─────────────────────
            SessionManager session = SessionManager.getInstance();

            if (!account.isAdmin()) {
                // Tài khoản Employee → load Employee từ DB rồi lưu cả hai vào session
                Employee emp = employeeDAO.findByAccountId(account.getAccountId());
                session.login(account, emp);   // lưu cả account lẫn employee
            } else {
                // Tài khoản Admin → chỉ cần account
                session.login(account);
            }
            // ─────────────────────────────────────────────────────────────────

            // MANAGER uses the full Admin dashboard; all other employees use
            // the Employee dashboard (which further filters by position).
            Employee emp = session.getCurrentEmployee();
            boolean isManager = !account.isAdmin()
                    && emp != null
                    && emp.getPosition() == Employee.Position.MANAGER;

            String path = (account.isAdmin() || isManager)
                    ? "/furniture_system/view/admin_dashboard.fxml"
                    : "/furniture_system/view/employee_dashboard.fxml";

            URL resource = getClass().getResource(path);
            if (resource == null) {
                showError("FXML not found: " + path);
                loginButton.setDisable(false);
                return;
            }
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            String title = (account.isAdmin())  ? "Furniture System - Admin"
                         : isManager             ? "Furniture System - Manager"
                         :                         "Furniture System - Employee";
            stage.setTitle(title);
            stage.setWidth(1100);
            stage.setHeight(680);
            stage.centerOnScreen();

        } catch (IOException e) {
            showError("Load dashboard failed: " + e.getMessage());
            loginButton.setDisable(false);
        }
    }
}