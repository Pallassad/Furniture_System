package furniture_system.service;

import furniture_system.dao.FurnitureTypeDAO;
import furniture_system.model.FurnitureType;

import java.sql.SQLException;
import java.util.List;

/**
 * Service layer for FurnitureType.
 * All business validation is handled here; the DAO is responsible only for DB queries.
 */
public class FurnitureTypeService {

    private final FurnitureTypeDAO dao = new FurnitureTypeDAO();

    // ── View All ─────────────────────────────────────────────────────────────
    public List<FurnitureType> getAll() throws SQLException {
        return dao.getAll();
    }

    // ── Search ───────────────────────────────────────────────────────────────
    public List<FurnitureType> search(String keyword) throws SQLException {
        if (keyword == null || keyword.trim().isEmpty()) return dao.getAll();
        return dao.search(keyword.trim());
    }

    // ── Get Active (for Employee ComboBox) ────────────────────────────────────
    public List<FurnitureType> getActive() throws SQLException {
        return dao.getActive();
    }

    // ── Add ──────────────────────────────────────────────────────────────────
    /**
     * @return null on success, error message on failure
     */
    public String add(String typeName, String description, String status) throws SQLException {
        String err = validate(typeName, -1);
        if (err != null) return err;
        boolean ok = dao.insert(new FurnitureType(typeName.trim(), description, status));
        return ok ? null : "Failed to add furniture type. Please try again.";
    }

    // ── Update ───────────────────────────────────────────────────────────────
    /**
     * @return null on success, error message on failure
     */
    public String update(int typeId, String typeName, String description, String status)
            throws SQLException {
        String err = validate(typeName, typeId);
        if (err != null) return err;
        FurnitureType ft = new FurnitureType(typeName.trim(), description, status);
        ft.setTypeId(typeId);
        boolean ok = dao.update(ft);
        return ok ? null : "Failed to update. Please try again.";
    }

    // ── Deactivate (Soft Delete) ──────────────────────────────────────────────
    /**
     * Soft-delete: only warn if there are still ACTIVE Products.
     * @return null on success, error message on failure
     */
    public String deactivate(int typeId) throws SQLException {
        if (dao.hasActiveLinkedProducts(typeId)) {
            return "This furniture type still has ACTIVE products.\n" +
                   "Please deactivate or delete related products before deactivating.";
        }
        boolean ok = dao.deactivate(typeId);
        return ok ? null : "Deactivation failed. Please try again.";
    }

    // ── Delete (Hard Delete) ──────────────────────────────────────────────────
    /**
     * Hard-delete: block if there are ANY Products (even INACTIVE).
     * @return null on success, error message on failure
     */
    public String delete(int typeId) throws SQLException {
        if (dao.hasLinkedProducts(typeId)) {
            return "This furniture type still has linked products (including deactivated products).\n" +
                   "Please delete all related products first.";
        }
        boolean ok = dao.delete(typeId);
        return ok ? null : "Delete failed. Please try again.";
    }

    // ── Validation ───────────────────────────────────────────────────────────
    /**
     * Validates input against the DB constraints:
     *   chk_type_name  : LEN(TypeName) >= 2
     *   uq_typename    : UNIQUE (case-insensitive)
     *   chk_type_status: Status IN ('ACTIVE','INACTIVE')
     *
     * @param excludeId TypeId to skip on update; -1 when inserting
     */
    private String validate(String typeName, int excludeId) throws SQLException {
        if (typeName == null || typeName.trim().length() < 2)
            return "Type Name must be at least 2 characters.";
        if (typeName.trim().length() > 100)
            return "Type Name must not exceed 100 characters.";
        if (dao.isTypeNameTaken(typeName, excludeId))
            return "The name \"" + typeName.trim() + "\" already exists. Please choose a different name.";
        return null;
    }
}