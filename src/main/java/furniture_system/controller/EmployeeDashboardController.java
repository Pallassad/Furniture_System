package furniture_system.controller;

import furniture_system.model.Employee;
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
import java.util.Set;

public class EmployeeDashboardController {

    @FXML private Label     lblUsername;
    @FXML private StackPane contentArea;

    @FXML private Button btnOrders;
    @FXML private Button btnCustomer;
    @FXML private Button btnFurnitureType;
    @FXML private Button btnProducts;
    @FXML private Button btnInventory;
    @FXML private Button btnPromotion;
    @FXML private Button btnWarranty;
    @FXML private Button btnDeliveryAddress;
    @FXML private Button btnSalary;

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
            btnOrders,
            btnCustomer,
            btnFurnitureType,
            btnProducts,
            btnInventory,
            btnPromotion,
            btnDeliveryAddress,
            btnWarranty,
            btnSalary
        );

        applyPositionPermissions();
    }

    // ── Position-based sidebar filtering ─────────────────────────────────────
    /**
     * Hides sidebar buttons that the current employee's position is not
     * allowed to access, then navigates to the appropriate landing page.
     *
     * SALES       → all employee buttons except Salary
     * WAREHOUSE   → Products, Furniture Types, Inventory only
     * ACCOUNTANT  → Salary only  (uses admin salary view)
     * TECHNICIAN  → Warranty Tickets only
     */
    private void applyPositionPermissions() {
        Employee emp = SessionManager.getInstance().getCurrentEmployee();
        Employee.Position pos = (emp != null) ? emp.getPosition() : null;

        if (pos == null || pos == Employee.Position.SALES) {
            // Full employee access — show everything EXCEPT Salary
            setAllButtonsVisible(true);
            btnSalary.setVisible(false);
            btnSalary.setManaged(false);
            setActive(btnCustomer);
            loadView("/furniture_system/view/employee_customer_management.fxml");
            return;
        }

        switch (pos) {
            case WAREHOUSE -> {
                // Products | Furniture Types | Inventory
                Set<Button> allowed = Set.of(btnProducts, btnFurnitureType, btnInventory);
                hideButtons(allowed);
                setActive(btnProducts);
                loadView("/furniture_system/view/employee_furniture_management.fxml");
            }
            case ACCOUNTANT -> {
                // Salary Management only — reuse the admin salary view
                Set<Button> allowed = Set.of(btnSalary);
                hideButtons(allowed);
                setActive(btnSalary);
                loadView("/furniture_system/view/admin_salary_management.fxml");
            }
            case TECHNICIAN -> {
                // Warranty Tickets only
                Set<Button> allowed = Set.of(btnWarranty);
                hideButtons(allowed);
                setActive(btnWarranty);
                loadWarrantyView();
            }
            default -> {
                // Fallback: full access
                setAllButtonsVisible(true);
                setActive(btnCustomer);
                loadView("/furniture_system/view/employee_customer_management.fxml");
            }
        }
    }

    /** Shows only the buttons in {@code allowed}; hides everything else. */
    private void hideButtons(Set<Button> allowed) {
        for (Button btn : sidebarButtons) {
            boolean show = allowed.contains(btn);
            btn.setVisible(show);
            btn.setManaged(show);
        }
    }

    private void setAllButtonsVisible(boolean visible) {
        for (Button btn : sidebarButtons) {
            btn.setVisible(visible);
            btn.setManaged(visible);
        }
    }

    // ── Sidebar handlers ──────────────────────────────────────────────────────

    @FXML
    public void showOrders() {
        setActive(btnOrders);
        loadView("/furniture_system/view/employee_order_management.fxml");
    }

    @FXML
    public void showCustomerManagement() {
        setActive(btnCustomer);
        loadView("/furniture_system/view/employee_customer_management.fxml");
    }

    @FXML
    public void showFurnitureTypeFilter() {
        setActive(btnFurnitureType);
        loadView("/furniture_system/view/employee_furniture_type_management.fxml");
    }

    @FXML
    public void showProducts() {
        setActive(btnProducts);
        loadView("/furniture_system/view/employee_furniture_management.fxml");
    }

    @FXML
    public void showInventory() {
        setActive(btnInventory);
        loadView("/furniture_system/view/employee_stock_view.fxml");
    }

    @FXML
    public void showPromotions() {
        setActive(btnPromotion);
        loadView("/furniture_system/view/employee_promotion_management.fxml");
    }

    @FXML
    public void showSalaryManagement() {
        setActive(btnSalary);
        loadView("/furniture_system/view/admin_salary_management.fxml");
    }

    @FXML
    public void showDeliveryAddressManagement() {
        setActive(btnDeliveryAddress);
        loadView("/furniture_system/view/employee_delivery_address_management.fxml");
    }

    // ── Warranty: uses FXMLLoader with controller to inject employeeId ─────────
    @FXML
    public void showWarranty() {
        setActive(btnWarranty);
        loadWarrantyView();
    }

    private void loadWarrantyView() {
        try {
            var url = getClass().getResource(
                    "/furniture_system/view/employee_warranty_management.fxml");
            if (url == null) {
                showPlaceholder("⚠ View not found: employee_warranty_management.fxml");
                return;
            }

            FXMLLoader loader = new FXMLLoader(url);
            Parent view = loader.load();

            // Inject employeeId from SessionManager into the controller
            EmployeeWarrantyController ctrl = loader.getController();
            var emp = SessionManager.getInstance().getCurrentEmployee();
            if (emp != null) {
                ctrl.setCurrentEmployeeId(emp.getEmployeeId());
            }

            if (contentArea.getChildren().isEmpty()) { contentArea.getChildren().add(0, view); } else { contentArea.getChildren().set(0, view); }
        } catch (IOException e) {
            showPlaceholder("⚠ Error loading warranty view: " + e.getMessage());
        }
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
            stage.setMaximized(false);
            stage.setResizable(false);
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
            if (contentArea.getChildren().isEmpty()) {
                contentArea.getChildren().add(0, view);
            } else {
                contentArea.getChildren().set(0, view);
            }
        } catch (IOException e) {
            showPlaceholder("⚠ Error loading: " + e.getMessage());
        }
    }

    private void showPlaceholder(String title) {
        Label lbl = new Label(title + "\n\nComing soon...");
        lbl.setStyle("-fx-font-size:18px; -fx-text-fill:#9e9e9e; -fx-font-style:italic;");
        lbl.setAlignment(javafx.geometry.Pos.CENTER);
        if (contentArea.getChildren().isEmpty()) {
            contentArea.getChildren().add(0, lbl);
        } else {
            contentArea.getChildren().set(0, lbl);
        }
    }
}