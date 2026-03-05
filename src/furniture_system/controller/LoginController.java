package furniture_system.controller;

import furniture_system.model.Account;
import furniture_system.service.AuthService;
import furniture_system.service.LoginResult;
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

    // ── PANE 0 : Sign In ──────────────────────────────────────
    @FXML private VBox          loginPane;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         errorLabel;

    // ── PANE 1 : Forgot Password - Step 1 (Enter Email) ───────
    @FXML private VBox      step1Pane;
    @FXML private TextField emailField;
    @FXML private Label     step1ErrorLabel;
    @FXML private Button    sendCodeButton;

    // ── PANE 2 : Forgot Password - Step 2 (OTP + New Password) ─
    @FXML private VBox          step2Pane;
    @FXML private TextField     otpField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label         step2ErrorLabel;
    @FXML private Label         step2InfoLabel;
    @FXML private Label         passwordMatchLabel;
    @FXML private Button        resetButton;

    // ── PANE 3 : Forgot Password - Step 3 (Success) ───────────
    @FXML private VBox  step3Pane;
    @FXML private Label summaryUsername;
    @FXML private Label summaryTime;

    private final AuthService authService = new AuthService();

    // ===========================================================
    // INIT
    // ===========================================================

    @FXML
    public void initialize() {
        showPane(loginPane);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // Enter key submits login
        passwordField.setOnAction(e -> handleLogin());

        // Live password-match feedback on Step 2
        confirmPasswordField.textProperty().addListener((obs, o, n) -> checkPasswordMatch());
        newPasswordField.textProperty().addListener((obs, o, n) -> checkPasswordMatch());
    }

    // ===========================================================
    // PANE 0 : SIGN IN
    // ===========================================================

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

    @FXML
    public void handleExit() {
        Platform.exit();
    }

    // ===========================================================
    // PANE 1 : FORGOT PASSWORD - STEP 1  (Enter Email)
    // ===========================================================

    /** "Forgot password?" link on Sign In screen */
    @FXML
    public void handleForgotPassword() {
        emailField.clear();
        hideLabel(step1ErrorLabel);
        showPane(step1Pane);
    }

    /** "Send Code" button on Step 1 */
    @FXML
    public void handleSendCode() {
        hideLabel(step1ErrorLabel);
        String email = emailField.getText().trim();

        if (email.isEmpty()) {
            showLabel(step1ErrorLabel, "Please enter your email address.");
            return;
        }

        sendCodeButton.setDisable(true);
        sendCodeButton.setText("Sending...");

        try {
            if (authService.requestPasswordReset(email)) {
                // Clear Step 2 fields before showing
                otpField.clear();
                newPasswordField.clear();
                confirmPasswordField.clear();
                hideLabel(step2ErrorLabel);
                hideLabel(passwordMatchLabel);
                step2InfoLabel.setText("Check your inbox. Code expires in 15 minutes.");
                showPane(step2Pane);
            } else {
                showLabel(step1ErrorLabel, "No account found with that email address.");
            }
        } catch (Exception ex) {
            String msg = ex.getMessage() != null
                    ? ex.getMessage()
                    : "Could not send email. Please try again.";
            showLabel(step1ErrorLabel, msg);
        } finally {
            sendCodeButton.setDisable(false);
            sendCodeButton.setText("Send Code \u2192");
        }
    }

    /** "Back to Login" button on Step 1 (and Step 3) */
    @FXML
    public void handleBackToLogin() {
        clearError();
        passwordField.clear();
        showPane(loginPane);
    }

    // ===========================================================
    // PANE 2 : FORGOT PASSWORD - STEP 2  (OTP + New Password)
    // ===========================================================

    /** "Reset Password" button on Step 2 */
    @FXML
    public void handleResetPassword() {
        hideLabel(step2ErrorLabel);

        String otp     = otpField.getText().trim();
        String newPass = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (otp.isEmpty()) {
            showLabel(step2ErrorLabel, "Please enter the reset code.");
            return;
        }
        if (newPass.isEmpty()) {
            showLabel(step2ErrorLabel, "Please enter a new password.");
            return;
        }
        if (!newPass.equals(confirm)) {
            showLabel(step2ErrorLabel, "Passwords do not match.");
            return;
        }

        resetButton.setDisable(true);
        resetButton.setText("Resetting...");

        try {
            authService.resetPasswordWithToken(otp, newPass, confirm);

            // Success -> go to Step 3
            summaryUsername.setText(emailField.getText().trim());
            summaryTime.setText(
                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            showPane(step3Pane);

        } catch (Exception ex) {
            String msg = ex.getMessage() != null
                    ? ex.getMessage()
                    : "Reset failed. Please try again.";
            showLabel(step2ErrorLabel, msg);
        } finally {
            resetButton.setDisable(false);
            resetButton.setText("Reset Password");
        }
    }

    /** "Back" button on Step 2 */
    @FXML
    public void handleBackToStep1() {
        hideLabel(step2ErrorLabel);
        hideLabel(passwordMatchLabel);
        showPane(step1Pane);
    }

    // ===========================================================
    // PRIVATE HELPERS
    // ===========================================================

    /** Show only the target pane; hide all others */
    private void showPane(VBox target) {
        for (VBox pane : new VBox[]{loginPane, step1Pane, step2Pane, step3Pane}) {
            boolean active = (pane == target);
            pane.setVisible(active);
            pane.setManaged(active);
        }
    }

    /** Real-time password match indicator while typing */
    private void checkPasswordMatch() {
        String newPass = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();
        if (confirm.isEmpty()) {
            hideLabel(passwordMatchLabel);
            return;
        }
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

    private void showLabel(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void hideLabel(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    private void clearError()          { hideLabel(errorLabel); }
    private void showError(String msg) { showLabel(errorLabel, msg); }

    /** Navigate to the correct dashboard after successful login */
    private void onLoginSuccess(Account account) {
        try {
            String path = account.isAdmin()
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
            stage.setTitle(account.isAdmin()
                    ? "Furniture System - Admin"
                    : "Furniture System - Employee");
            stage.setWidth(1100);
            stage.setHeight(680);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Load dashboard failed: " + e.getMessage());
            loginButton.setDisable(false);
        }
    }
}