package furniture_system.controller;

import furniture_system.model.FurnitureType;
import furniture_system.model.Product;
import furniture_system.service.FurnitureTypeService;
import furniture_system.service.ProductService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;
import javafx.scene.control.cell.PropertyValueFactory;

public class EmployeeFurnitureTypeController implements Initializable {

    @FXML private ComboBox<FurnitureType> cmbTypeFilter;
    @FXML private Button btnApplyFilter;
    @FXML private Button btnResetFilter;

    @FXML private TableView<Product> tblProducts;
    @FXML private TableColumn<Product, Integer> colProductId;
    @FXML private TableColumn<Product, String> colProductName;
    @FXML private TableColumn<Product, String> colProductType;
    @FXML private TableColumn<Product, BigDecimal> colProductPrice;
    @FXML private TableColumn<Product, String> colProductStatus;

    @FXML private TextField txtProductSearch;
    @FXML private Label lblResultCount;

    private final FurnitureTypeService typeService = new FurnitureTypeService();
    private final ProductService productService = new ProductService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupProductTable();
        loadFurnitureTypeFilter();
        loadAllProducts();  // Load dữ liệu thật ngay khi mở trang
    }

    // Load các loại nội thất ACTIVE vào ComboBox
    private void loadFurnitureTypeFilter() {
        try {
            List<FurnitureType> types = typeService.getActive();

            // Thêm option "Tất cả"
            FurnitureType allOption = new FurnitureType();
            allOption.setTypeId(0);
            allOption.setTypeName("— Tất cả loại —");
            types.add(0, allOption);

            cmbTypeFilter.setItems(FXCollections.observableArrayList(types));
            cmbTypeFilter.setValue(allOption);

            // Tự động lọc khi thay đổi lựa chọn
            cmbTypeFilter.setOnAction(e -> handleFilterByType());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi tải loại nội thất", e.getMessage());
        }
    }

    // Lọc sản phẩm theo loại + tìm kiếm theo tên
    @FXML
    private void handleFilterByType() {
        FurnitureType selected = cmbTypeFilter.getValue();
        String keyword = txtProductSearch.getText().trim();

        try {
            List<Product> filtered;

            if (selected == null || selected.getTypeId() == 0) {
                // Tất cả loại + tìm kiếm theo tên (dùng method có sẵn)
                filtered = productService.searchActiveProducts(keyword, null, null);
            } else {
                // Theo loại cụ thể + tìm kiếm theo tên
                // Vì ProductService chưa có getByType, ta dùng searchActiveProducts với keyword
                // và lọc thêm theo typeId ở đây (cách tạm thời)
                List<Product> allBySearch = productService.searchActiveProducts(keyword, null, null);
                filtered = allBySearch.stream()
                        .filter(p -> p.getTypeId() == selected.getTypeId())
                        .toList();
            }

            tblProducts.setItems(FXCollections.observableArrayList(filtered));
            updateResultCount(filtered.size());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi lọc sản phẩm", e.getMessage());
        }
    }

    // Reset filter về mặc định
    @FXML
    private void handleResetFilter() {
        cmbTypeFilter.setValue(cmbTypeFilter.getItems().get(0)); // "Tất cả"
        txtProductSearch.clear();
        loadAllProducts();
    }

    // Load tất cả sản phẩm ACTIVE
    private void loadAllProducts() {
        try {
            List<Product> activeProducts = productService.getActiveProducts();
            tblProducts.setItems(FXCollections.observableArrayList(activeProducts));
            updateResultCount(activeProducts.size());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi tải sản phẩm", e.getMessage());
        }
    }

    // Cấu hình bảng sản phẩm
    private void setupProductTable() {
        colProductId.setCellValueFactory(new PropertyValueFactory<>("productId"));
        colProductName.setCellValueFactory(new PropertyValueFactory<>("name"));

        // Hiển thị tên loại (typeName) thay vì ID
        colProductType.setCellValueFactory(cell -> 
            new SimpleStringProperty(cell.getValue().getTypeName() != null 
                ? cell.getValue().getTypeName() : "—"));

        colProductPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colProductStatus.setCellValueFactory(cell -> new SimpleStringProperty("Active"));

        // Format giá tiền đẹp
        colProductPrice.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? "—" : String.format("%,.0f ₫", price));
            }
        });

        tblProducts.setPlaceholder(new Label("Không có sản phẩm nào."));
    }

    private void updateResultCount(int count) {
        lblResultCount.setText("Kết quả: " + count + " sản phẩm");
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}