package furniture_system.controller;

import furniture_system.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.Scene;
import java.io.IOException;
import java.util.List;

public class AdminDashboardController {

    @FXML private Label     lblUsername;
    @FXML private StackPane contentArea;

    @FXML private Button btnAuth;
    @FXML private Button btnEmployee;
    @FXML private Button btnCustomer;
    @FXML private Button btnProduct;
    @FXML private Button btnFurnitureType;
    @FXML private Button btnOrders;
    @FXML private Button btnPromotion;
    @FXML private Button btnReports;
    @FXML private Button btnSupplier;
    @FXML private Button btnStock;
    @FXML private Button btnSalary;
    @FXML private Button btnWarranty;
    @FXML private Button btnDeliveryAddress;

    private List<Button> sidebarButtons;

    private static final String ACTIVE_STYLE =
            "-fx-background-color:#3949ab; -fx-text-fill:white; " +
            "-fx-background-radius:6; -fx-cursor:hand; " +
            "-fx-alignment:CENTER_LEFT; -fx-padding:10 12; -fx-font-weight:bold;";

    private static final String INACTIVE_STYLE =
            "-fx-background-color:transparent; -fx-text-fill:#c5cae9; " +
            "-fx-background-radius:6; -fx-cursor:hand; " +
            "-fx-alignment:CENTER_LEFT; -fx-padding:10 12; -fx-font-weight:normal;";

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        if (SessionManager.getInstance().getCurrentAccount() != null) {
            lblUsername.setText("Welcome, " +
                SessionManager.getInstance().getCurrentAccount().getUsername());
        }

        sidebarButtons = List.of(
            btnReports,
            btnAuth, btnEmployee, btnCustomer,
            btnProduct, btnFurnitureType,
            btnOrders, btnPromotion,
            btnSupplier, btnStock,
            btnSalary, btnWarranty,
            btnDeliveryAddress
        );

        setActive(btnReports);
        loadView("/furniture_system/view/admin_reports.fxml");
    }

    // ── Sidebar handlers ──────────────────────────────────────────────────────

    @FXML
    public void showAuthManagement() {
        setActive(btnAuth);
        loadView("/furniture_system/view/auth_management.fxml");
    }

    @FXML
    public void showEmployeeManagement() {
        setActive(btnEmployee);
        loadView("/furniture_system/view/employee_management.fxml");
    }

    @FXML
    public void showCustomerManagement() {
        setActive(btnCustomer);
        loadView("/furniture_system/view/customer_management.fxml");
    }

    @FXML
    public void showProductManagement() {
        setActive(btnProduct);
        loadView("/furniture_system/view/furniture_management.fxml");
    }

    @FXML
    public void showFurnitureTypeManagement() {
        setActive(btnFurnitureType);
        loadView("/furniture_system/view/furniture_type_management.fxml");
    }

    @FXML
    public void showOrders() {
        setActive(btnOrders);
        loadView("/furniture_system/view/admin_order_management.fxml");
    }

    @FXML
    public void showPromotionManagement() {       // ← MỚI
        setActive(btnPromotion);
        loadView("/furniture_system/view/admin_promotion_management.fxml");
    }

    @FXML
    public void showSupplierManagement() {
        setActive(btnSupplier);
        loadView("/furniture_system/view/supplier_management.fxml");
    }

    @FXML
    public void showStockManagement() {
        setActive(btnStock);
        loadView("/furniture_system/view/stock_management.fxml");
    }

    @FXML
    public void showSalaryManagement() {
        setActive(btnSalary);
        loadView("/furniture_system/view/admin_salary_management.fxml");
    }

    @FXML
    public void showWarrantyManagement() {
        setActive(btnWarranty);
        loadView("/furniture_system/view/admin_warranty_management.fxml");
    }

    @FXML
    public void showDeliveryAddressManagement() {
        setActive(btnDeliveryAddress);
        loadView("/furniture_system/view/admin_delivery_address_management.fxml");
    }

    @FXML
    public void showReports() {
        setActive(btnReports);
        loadView("/furniture_system/view/admin_reports.fxml");
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
            if (url == null) {
                showPlaceholder("⚠ View not found: " + fxmlPath);
                return;
            }
            Parent view = FXMLLoader.load(url);
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
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