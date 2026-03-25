// Updated for SQL Server schema - isDuplicateKeyError added
package furniture_system.service;

import furniture_system.dao.SupplierDAO;
import furniture_system.model.Supplier;
import furniture_system.model.SupplierProduct;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public class SupplierService {

    private final SupplierDAO dao = new SupplierDAO();

    // ══════════════════════════════════════════════════════════════════════
    //  SUPPLIER
    // ══════════════════════════════════════════════════════════════════════

    public List<Supplier> getAllSuppliers() {
        try { return dao.getAll(); }
        catch (SQLException e) { throw new RuntimeException("DB error: " + e.getMessage(), e); }
    }

    public List<Supplier> searchSuppliers(String keyword) {
        if (keyword == null || keyword.isBlank()) return getAllSuppliers();
        try { return dao.search(keyword); }
        catch (SQLException e) { throw new RuntimeException("DB error: " + e.getMessage(), e); }
    }

    /**
     * Add new supplier – validates all business rules.
     * @throws IllegalArgumentException on validation failure
     */
    public void addSupplier(Supplier s) {
        validateSupplier(s);
        try {
            if (!dao.insert(s))
                throw new RuntimeException("Insert failed – no rows affected.");
        } catch (SQLException e) {
            if (isDuplicateKeyError(e))
                throw new IllegalArgumentException("Phone or Email is already used by another supplier.");
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    /**
     * Update supplier – validates business rules.
     */
    public void updateSupplier(Supplier s) {
        if (s.getSupplierId() <= 0)
            throw new IllegalArgumentException("Invalid Supplier ID.");
        validateSupplier(s);
        try {
            if (!dao.update(s))
                throw new RuntimeException("Update failed – supplier not found.");
        } catch (SQLException e) {
            if (isDuplicateKeyError(e))
                throw new IllegalArgumentException("Phone or Email is already used by another supplier.");
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    /** Soft-delete: marks supplier as INACTIVE */
    public void deactivateSupplier(int supplierId) {
        if (supplierId <= 0) throw new IllegalArgumentException("Invalid Supplier ID.");
        try {
            if (!dao.deactivate(supplierId))
                throw new RuntimeException("Deactivate failed – supplier not found.");
        } catch (SQLException e) {
            throw new RuntimeException("DB error: " + e.getMessage(), e);
        }
    }

    /**
     * Hard-delete Supplier.
     * Điều kiện: không còn Product nào có supplierId trỏ tới Supplier này.
     * SupplierProduct links sẽ bị xoá cascade trong transaction.
     */
    public void deleteSupplier(int supplierId) {
        if (supplierId <= 0) throw new IllegalArgumentException("Invalid Supplier ID.");
        try {
            if (dao.hasLinkedProducts(supplierId))
                throw new IllegalStateException(
                    "Nhà cung cấp này còn sản phẩm liên kết. " +
                    "Vui lòng xoá hoặc chuyển nhà cung cấp cho tất cả sản phẩm trước.");
            if (!dao.hardDelete(supplierId))
                throw new RuntimeException("Xoá nhà cung cấp thất bại.");
        } catch (IllegalStateException e) {
            throw e;
        } catch (SQLException e) {
            throw new RuntimeException("DB error: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SUPPLIER-PRODUCT LINKS
    // ══════════════════════════════════════════════════════════════════════

    public List<SupplierProduct> getLinkedProducts(int supplierId) {
        try { return dao.getLinkedProducts(supplierId); }
        catch (SQLException e) { throw new RuntimeException("DB error: " + e.getMessage(), e); }
    }

    /**
     * 3.13.6 – Link supplier to product with import price & lead time.
     */
    public void linkProduct(int supplierId, int productId,
                            BigDecimal importPrice, int leadTimeDays) {
        if (supplierId <= 0 || productId <= 0)
            throw new IllegalArgumentException("Supplier and Product must be selected.");
        if (importPrice == null || importPrice.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Import Price must be greater than 0.");
        if (leadTimeDays < 0)
            throw new IllegalArgumentException("Lead Time Days cannot be negative.");

        SupplierProduct sp = new SupplierProduct(supplierId, productId, importPrice, leadTimeDays);
        try {
            dao.linkProduct(sp);
        } catch (SQLException e) {
            throw new RuntimeException("DB error: " + e.getMessage(), e);
        }
    }

    public void unlinkProduct(int supplierId, int productId) {
        try { dao.unlinkProduct(supplierId, productId); }
        catch (SQLException e) { throw new RuntimeException("DB error: " + e.getMessage(), e); }
    }

    // ── Validation ─────────────────────────────────────────────────────────

    private void validateSupplier(Supplier s) {
        if (s.getName() == null || s.getName().trim().length() < 2)
            throw new IllegalArgumentException("Name must be at least 2 characters.");
        if (s.getName().trim().length() > 150)
            throw new IllegalArgumentException("Name must not exceed 150 characters.");
        if (s.getPhone() == null || !s.getPhone().matches("\\d{9,11}"))
            throw new IllegalArgumentException("Phone must contain 9 to 11 digits only.");
        if (s.getEmail() != null && !s.getEmail().isBlank()
                && !s.getEmail().matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$"))
            throw new IllegalArgumentException("Email format is invalid.");
        if (s.getEmail() != null && s.getEmail().length() > 100)
            throw new IllegalArgumentException("Email must not exceed 100 characters.");
        if (s.getAddress() != null && s.getAddress().length() > 255)
            throw new IllegalArgumentException("Address must not exceed 255 characters.");
        if (s.getStatus() == null)
            throw new IllegalArgumentException("Status is required.");
    }

    /**
     * Detects SQL Server unique/duplicate key constraint violations.
     * Error code 2627 = unique constraint violated.
     * Error code 2601 = duplicate key row in unique index.
     */
    private boolean isDuplicateKeyError(SQLException e) {
        String msg = e.getMessage();
        return (msg != null && (msg.contains("UNIQUE") || msg.contains("duplicate key")))
            || e.getErrorCode() == 2627
            || e.getErrorCode() == 2601;
    }
}
