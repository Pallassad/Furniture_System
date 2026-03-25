package furniture_system.service;

import furniture_system.dao.ProductDAO;
import furniture_system.model.Product;
import furniture_system.model.Supplier;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Business logic layer for Product management.
 * Validation mirrors the SQL Server CHECK constraints:
 *   - LEN(Name) >= 2  and  <= 150   (chk_prod_name)
 *   - Price > 0                      (chk_prod_price)
 *   - WarrantyMonths >= 0            (chk_prod_warranty)
 *   - Status IN (ACTIVE|INACTIVE|OUT_OF_STOCK)  (chk_prod_status)
 */
public class ProductService {

    private final ProductDAO dao = new ProductDAO();

    // ── Validation (mirrors DB chk_ constraints) ──────────────────────────
    private void validate(Product p) throws IllegalArgumentException {
        String name = p.getName() == null ? "" : p.getName().trim();
        if (name.length() < 2)
            throw new IllegalArgumentException(
                    "Product name must be at least 2 characters.");
        if (name.length() > 150)
            throw new IllegalArgumentException(
                    "Product name must not exceed 150 characters.");
        if (p.getTypeId() <= 0)
            throw new IllegalArgumentException("Please select a Furniture Type.");
        if (p.getSupplierId() <= 0)
            throw new IllegalArgumentException("Please select a Supplier.");
        if (p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Price must be greater than 0.");
        if (p.getWarrantyMonths() < 0)
            throw new IllegalArgumentException("Warranty months cannot be negative.");
        if (p.getStatus() == null)
            throw new IllegalArgumentException("Please select a product status.");
    }

    // ── Admin – full CRUD ──────────────────────────────────────────────────

    public List<Product> getAllProducts() throws SQLException {
        return dao.getAll();
    }

    public List<Product> searchProducts(String keyword) throws SQLException {
        if (keyword == null || keyword.isBlank()) return getAllProducts();
        return dao.search(keyword.trim());
    }

    /**
     * Add a new product.
     * @throws IllegalArgumentException if validation fails (caught by controller)
     * @throws SQLException             if DB insert fails
     */
    public void addProduct(Product p) throws SQLException {
        p.setName(p.getName().trim());
        validate(p);
        if (!dao.insert(p))
            throw new SQLException("Insert returned 0 rows – product not saved.");
    }

    /**
     * Update an existing product.
     * @throws IllegalArgumentException if productId invalid or validation fails
     * @throws SQLException             if DB update fails
     */
    public void updateProduct(Product p) throws SQLException {
        if (p.getProductId() <= 0)
            throw new IllegalArgumentException("Invalid product ID.");
        p.setName(p.getName().trim());
        validate(p);
        if (!dao.update(p))
            throw new SQLException("Update returned 0 rows – record may not exist.");
    }

    /**
     * Soft-delete: sets Status to INACTIVE.
     * Mirrors the spec: "Admin can set product Status to INACTIVE or OUT_OF_STOCK."
     */
    public void deactivateProduct(int productId) throws SQLException {
        if (productId <= 0)
            throw new IllegalArgumentException("Invalid product ID.");
        if (!dao.deactivate(productId))
            throw new SQLException("Deactivate returned 0 rows – record may not exist.");
    }

    /**
     * Hard-delete sản phẩm.
     * Điều kiện:
     *  - Không còn OrderLine nào tham chiếu
     *  - Không còn WarrantyTicket nào tham chiếu
     * Khi xoá sẽ cascade xoá: SupplierProduct, StockLog, Stock của sản phẩm.
     */
    public void deleteProduct(int productId) throws SQLException {
        if (productId <= 0)
            throw new IllegalArgumentException("Invalid product ID.");
        if (dao.hasOrderLines(productId))
            throw new IllegalStateException(
                "Sản phẩm này đã xuất hiện trong đơn hàng. Không thể xoá vĩnh viễn.");
        if (dao.hasWarrantyTickets(productId))
            throw new IllegalStateException(
                "Sản phẩm này còn phiếu bảo hành. Vui lòng xoá tất cả phiếu bảo hành trước.");
        if (!dao.hardDelete(productId))
            throw new SQLException("Xoá sản phẩm thất bại.");
    }

    // ── Employee – read-only ───────────────────────────────────────────────

    public List<Product> getActiveProducts() throws SQLException {
        return dao.getActive();
    }

    public List<Product> searchActiveProducts(String keyword,
                                               BigDecimal minPrice,
                                               BigDecimal maxPrice) throws SQLException {
        return dao.searchActive(keyword, minPrice, maxPrice);
    }
    

    // ── Combo helpers ──────────────────────────────────────────────────────

    public List<Supplier> getAllSuppliers() throws SQLException {
        return dao.getAllSuppliers();
    }

    public List<Supplier> getActiveSuppliers() throws SQLException {
        return dao.getActiveSuppliers();
    }
}