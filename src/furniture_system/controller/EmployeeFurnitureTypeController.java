package furniture_system.controller;

import furniture_system.model.FurnitureType;
import furniture_system.service.FurnitureTypeService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for Employee – Function 4.4.1 Filter Products by Furniture Type.
 *
 * Integrate into your existing EmployeeDashboardController:
 *   1. Copy the @FXML fields + methods into that class
 *   2. Call loadFurnitureTypeFilter() from initialize()
 *   3. Connect handleFilterByType() with your real ProductService (see TODO below)
 */
public class EmployeeFurnitureTypeController implements Initializable {

    // ── Furniture Type Filter ─────────────────────────────────────────────────
    @FXML private ComboBox<FurnitureType> cmbTypeFilter;
    @FXML private Button btnApplyFilter;
    @FXML private Button btnResetFilter;

    // ── Products TableView (replace Object with your actual Product model) ───
    @FXML private TableView<Object>           tblProducts;
    @FXML private TableColumn<Object, Integer> colProductId;
    @FXML private TableColumn<Object, String>  colProductName;
    @FXML private TableColumn<Object, String>  colProductType;
    @FXML private TableColumn<Object, Double>  colProductPrice;
    @FXML private TableColumn<Object, String>  colProductStatus;

    // ── Search ────────────────────────────────────────────────────────────────
    @FXML private TextField txtProductSearch;
    @FXML private Label     lblResultCount;

    private final FurnitureTypeService furnitureTypeService = new FurnitureTypeService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupProductTable();
        loadFurnitureTypeFilter();
        loadAllProducts();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Load ACTIVE types into ComboBox
    // ════════════════════════════════════════════════════════════════════════
    private void loadFurnitureTypeFilter() {
        try {
            List<FurnitureType> types = furnitureTypeService.getActive();

            // First element: "— All Types —"
            FurnitureType allOption = new FurnitureType();
            allOption.setTypeId(0);
            allOption.setTypeName("— All Types —");
            types.add(0, allOption);

            cmbTypeFilter.setItems(FXCollections.observableArrayList(types));
            cmbTypeFilter.setValue(allOption);

            // Listener: filter immediately when selecting from ComboBox (no button required)
            cmbTypeFilter.setOnAction(e -> handleFilterByType());

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Category Loading Error", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Filter products by selected TypeId (4.4.1)
    // ════════════════════════════════════════════════════════════════════════
    @FXML
    private void handleFilterByType() {
        FurnitureType selected = cmbTypeFilter.getValue();

        if (selected == null || selected.getTypeId() == 0) {
            loadAllProducts();
            return;
        }

        int selectedTypeId = selected.getTypeId();

        /*
         * ══════════════════════════════════════════════════════════════
         *  TODO: Replace the section below with your actual ProductService
         *
         *  Example:
         *    List<Product> products = productService.getByType(selectedTypeId);
         *    tblProducts.setItems(FXCollections.observableArrayList(products));
         *    updateResultCount(products.size());
         *
         *  Corresponding SQL:
         *    SELECT p.ProductId, p.Name, ft.TypeName, p.Price, p.Status
         *    FROM Product p
         *    JOIN FurnitureType ft ON ft.TypeId = p.TypeId
         *    WHERE p.TypeId = @TypeId
         *    AND   p.Status <> 'INACTIVE'
         *    ORDER BY p.Name
         * ══════════════════════════════════════════════════════════════
         */
        System.out.println("[Employee] Filtering products by TypeId = " + selectedTypeId
                         + " (" + selected.getTypeName() + ")");

        // Placeholder – remove this line after connecting ProductService
        tblProducts.setItems(FXCollections.observableArrayList());
        updateResultCount(0);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Reset filter
    // ════════════════════════════════════════════════════════════════════════
    @FXML
    private void handleResetFilter() {
        loadFurnitureTypeFilter();
        loadAllProducts();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Load all products (no filter)
    // ════════════════════════════════════════════════════════════════════════
    private void loadAllProducts() {
        /*
         * TODO: replace with productService.getAll()
         *    List<Product> products = productService.getAll();
         *    tblProducts.setItems(FXCollections.observableArrayList(products));
         *    updateResultCount(products.size());
         */
        tblProducts.setItems(FXCollections.observableArrayList());
        updateResultCount(0);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Table setup
    // ════════════════════════════════════════════════════════════════════════
    private void setupProductTable() {
        /*
         * TODO: bind PropertyValueFactory to your actual Product model
         *
         *   colProductId.setCellValueFactory(new PropertyValueFactory<>("productId"));
         *   colProductName.setCellValueFactory(new PropertyValueFactory<>("name"));
         *   colProductType.setCellValueFactory(new PropertyValueFactory<>("typeName"));
         *   colProductPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
         *   colProductStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
         */
        tblProducts.setPlaceholder(new Label("No products available."));
    }

    private void updateResultCount(int count) {
        if (lblResultCount != null)
            lblResultCount.setText("Results: " + count + " products");
    }

    // ── Utility ──────────────────────────────────────────────────────────────
    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.setTitle(title); 
        a.setHeaderText(null); 
        a.showAndWait();
    }
}