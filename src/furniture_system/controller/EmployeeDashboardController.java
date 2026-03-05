package furniture_system.controller;

import furniture_system.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.List;

public class EmployeeDashboardController {

    @FXML private Label     lblUsername;
    @FXML private StackPane contentArea;

    @FXML private Button btnOrders;
    @FXML private Button btnCustomer;
    @FXML private Button btnFurnitureType;
    @FXML private Button btnProducts;      // ★ MỚI
    @FXML private Button btnInventory;
    @FXML private Button btnWarranty;

    private List<Button> sidebarButtons;

    private static final String ACTIVE_STYLE =
            "-fx-background-color:#388e3c; -fx-text-fill:white; " +
            "-fx-background-radius:6; -fx-cursor:hand; " +
            "-fx-alignment:CENTER_LEFT; -fx-padding:10 12; -fx-font-weight:bold;";

    private static final String INACTIVE_STYLE =
            "-fx-background-color:transparent; -fx-text-fill:#c8e6c9; " +
            "-fx-background-radius:6; -fx-cursor:hand; " +
            "-fx-alignment:CENTER_LEFT; -fx-padding:10 12; -fx-font-weight:normal;";

    @FXML
    public void initialize() {
        if (SessionManager.getInstance().getCurrentAccount() != null) {
            lblUsername.setText("Welcome, " +
                SessionManager.getInstance().getCurrentAccount().getUsername());
        }

        sidebarButtons = List.of(
            btnOrders, btnCustomer,
            btnFurnitureType,
            btnProducts,                        // ★ MỚI
            btnInventory, btnWarranty
        );

        setActive(btnOrders);
        showPlaceholder("🛒 Sales / Orders");
    }

    // ── Sidebar handlers ──────────────────────────────────────────────────────

    @FXML
    public void showOrders() {
        setActive(btnOrders);
        showPlaceholder("🛒 Sales / Orders");
    }

    @FXML
    public void showCustomerManagement() {
        setActive(btnCustomer);
        loadView("/furniture_system/view/customer_management.fxml");
    }

    @FXML
    public void showFurnitureTypeFilter() {
        setActive(btnFurnitureType);
        loadView("/furniture_system/view/employee_product_filter.fxml");
    }

    @FXML
    public void showProducts() {                // ★ MỚI
        setActive(btnProducts);
        loadView("/furniture_system/view/employee_furniture_management.fxml");
    }

    @FXML
    public void showInventory() {
        setActive(btnInventory);
        loadView("/furniture_system/view/employee_stock_view.fxml"); // ★ ĐÃ CẬP NHẬT
    }

    @FXML
    public void showWarranty() {
        setActive(btnWarranty);
        showPlaceholder("🔧 Warranty Tickets");
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @FXML
    public void handleLogout() {
        SessionManager.getInstance().logout();
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/furniture_system/view/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) lblUsername.getScene().getWindow();
            stage.setScene(new Scene(root, 780, 560));
            stage.setTitle("Fair Deal Furniture – Login");
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setActive(Button active) {
        for (Button btn : sidebarButtons) {
            btn.setStyle(btn == active ? ACTIVE_STYLE : INACTIVE_STYLE);
        }
    }

    private void loadView(String fxmlPath) {
        try {
            var url = getClass().getResource(fxmlPath);
            if (url == null) { showPlaceholder("⚠ View not found: " + fxmlPath); return; }
            Parent view = FXMLLoader.load(url);
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            showPlaceholder("⚠ Error loading: " + e.getMessage());
        }
    }

    private void showPlaceholder(String title) {
        Label lbl = new Label(title + "\n\nComing soon...");
        lbl.setStyle("-fx-font-size:18px; -fx-text-fill:#9e9e9e; -fx-font-style:italic;");
        lbl.setAlignment(javafx.geometry.Pos.CENTER);
        contentArea.getChildren().setAll(lbl);
    }
}