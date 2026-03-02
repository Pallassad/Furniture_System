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
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class LoginController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         errorLabel;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
        passwordField.setOnAction(event -> handleLogin());
    }

    @FXML
    public void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        loginButton.setDisable(true);
        clearError();

        LoginResult result = authService.login(username, password);

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

    private void onLoginSuccess(Account account) {
        try {
            String fxmlPath = account.isAdmin()
                    ? "/furniture_system/view/admin_dashboard.fxml"
                    : "/furniture_system/view/employee_dashboard.fxml";

            // ── Kiểm tra file FXML có tồn tại không trước khi load ──────────
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                showError("Không tìm thấy file giao diện: " + fxmlPath
                        + "\nHãy đảm bảo file đã được thêm vào project.");
                loginButton.setDisable(false);
                return;
            }

            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(account.isAdmin()
                    ? "Furniture System – Admin Dashboard"
                    : "Furniture System – Employee Dashboard");
            stage.setWidth(1100);
            stage.setHeight(680);
            stage.centerOnScreen();

        } catch (IOException e) {
            showError("Lỗi load dashboard: " + e.getMessage());
            loginButton.setDisable(false);
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void clearError() {
        errorLabel.setVisible(false);
    }
}