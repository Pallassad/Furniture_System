package furniture_system.controller;

// ============================================================
// Integration Guide – How to wire the two new modules into
// AdminDashboardController and EmployeeDashboardController.
//
// These are CODE SNIPPETS only.  Merge them into your existing
// controllers; do NOT replace the entire file.
// ============================================================

/*
 * ──────────────────────────────────────────────────────────────
 * ADMIN DASHBOARD  (AdminDashboardController.java)
 * ──────────────────────────────────────────────────────────────
 *
 * 1. Add FXML injection for the two new sidebar buttons that
 *    already exist in AdminDashboard.fxml:
 *
 *   @FXML private Button btnWarranty;   // add to existing @FXML block
 *   @FXML private Button btnSalary;     // add to existing @FXML block
 *
 * 2. Add two handler methods:
 */

class AdminDashboardController_Snippets {

    // Existing injected field – keep as-is:
    // @FXML private StackPane contentArea;

    /** Called by btnWarranty → onAction="#showWarrantyManagement" */
    public void showWarrantyManagement() {
        loadContent("/furniture_system/view/admin_warranty_management.fxml");
    }

    /** Called by a new "💰 Salary" sidebar button → onAction="#showSalaryManagement" */
    public void showSalaryManagement() {
        loadContent("/furniture_system/view/admin_salary_management.fxml");
    }

    // Typical loadContent helper (already present in your project):
    private void loadContent(String fxmlPath) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource(fxmlPath));
            javafx.scene.Node view = loader.load();
            // contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/*
 * ──────────────────────────────────────────────────────────────
 * EMPLOYEE DASHBOARD  (EmployeeDashboardController.java)
 * ──────────────────────────────────────────────────────────────
 *
 * The Employee sidebar already has:
 *   <Button fx:id="btnWarranty" onAction="#showWarranty" …/>
 *
 * Replace / add:
 */

class EmployeeDashboardController_Snippets {

    // Injected by FXML – already present:
    // @FXML private StackPane contentArea;
    // private int loggedInEmployeeId;  // set during login

    /** Called by btnWarranty sidebar button */
    public void showWarranty() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource(
                            "/furniture_system/view/emp_warranty_management.fxml"));
            javafx.scene.Node view = loader.load();

            // Inject the current employee ID into the controller
            EmpWarrantyController ctrl = loader.getController();
            // ctrl.setCurrentEmployeeId(loggedInEmployeeId);

            // contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/*
 * ──────────────────────────────────────────────────────────────
 * ADMIN DASHBOARD FXML  (AdminDashboard.fxml) – additions
 * ──────────────────────────────────────────────────────────────
 *
 * Add these two sidebar buttons inside the existing <VBox> sidebar
 * (after the Stock button and before the Reports button):
 *
 *   <Button fx:id="btnWarranty" text="🔧  Warranty Tickets"
 *           onAction="#showWarrantyManagement"
 *           maxWidth="Infinity"
 *           style="-fx-background-color:transparent; -fx-text-fill:#c5cae9;
 *                  -fx-background-radius:6; -fx-cursor:hand;
 *                  -fx-alignment:CENTER_LEFT; -fx-padding:10 12;"/>
 *
 *   <Button fx:id="btnSalary" text="💰  Salary Management"
 *           onAction="#showSalaryManagement"
 *           maxWidth="Infinity"
 *           style="-fx-background-color:transparent; -fx-text-fill:#c5cae9;
 *                  -fx-background-radius:6; -fx-cursor:hand;
 *                  -fx-alignment:CENTER_LEFT; -fx-padding:10 12;"/>
 * ──────────────────────────────────────────────────────────────
 */
