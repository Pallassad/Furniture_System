package furniture_system.service;

import furniture_system.dao.PromotionDAO;
import furniture_system.model.Promotion;
import furniture_system.utils.SessionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PromotionService – business logic + validation for Promotion Management.
 * Admin  : full CRUD (view, add, update, disable, search).
 * Employee : read-only; apply logic lives in OrderService.calcDiscount().
 */
public class PromotionService {

    private final PromotionDAO dao = new PromotionDAO();

    // ── Guards ────────────────────────────────────────────────────────────────

    private void requireLogin() {
        if (!SessionManager.getInstance().isLoggedIn())
            throw new SecurityException("You must be logged in.");
    }

    private void requireAdmin() {
        if (!SessionManager.getInstance().isAdmin())
            throw new SecurityException("Access denied: Admin only.");
    }

    // ── 3.6.1  View All ───────────────────────────────────────────────────────

    public List<Promotion> getAll() {
        requireAdmin();
        try {
            return dao.findAll();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load promotions: " + e.getMessage(), e);
        }
    }

    // ── 3.6.5  Search ─────────────────────────────────────────────────────────

    /**
     * Filter in-memory on the full list.
     * Matches against Code, Name, and Status (case-insensitive).
     * Date range: returns promos whose period overlaps [from, to] (both nullable).
     */
    public List<Promotion> search(String keyword,
                                  String status,
                                  LocalDateTime from,
                                  LocalDateTime to) {
        requireAdmin();
        List<Promotion> all;
        try {
            all = dao.findAll();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load promotions: " + e.getMessage(), e);
        }

        String kw = (keyword == null) ? "" : keyword.trim().toLowerCase();

        return all.stream()
                .filter(p -> {
                    // keyword filter (Code or Name)
                    if (!kw.isBlank()) {
                        boolean matchCode = p.getCode().toLowerCase().contains(kw);
                        boolean matchName = p.getName().toLowerCase().contains(kw);
                        if (!matchCode && !matchName) return false;
                    }
                    // status filter
                    if (status != null && !status.isBlank()) {
                        if (!status.equalsIgnoreCase(p.getStatus())) return false;
                    }
                    // date range filter: promo period must overlap [from, to]
                    if (from != null && p.getEndDate() != null && p.getEndDate().isBefore(from))
                        return false;
                    if (to != null && p.getStartDate() != null && p.getStartDate().isAfter(to))
                        return false;
                    return true;
                })
                .toList();
    }

    // ── 3.6.2  Add New Promotion ──────────────────────────────────────────────

    public void addPromotion(Promotion p) {
        requireAdmin();
        validate(p, -1);
        try {
            dao.insert(p);
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert promotion: " + e.getMessage(), e);
        }
    }

    // ── 3.6.3  Update Promotion ───────────────────────────────────────────────

    public void updatePromotion(Promotion p) {
        requireAdmin();
        validate(p, p.getPromoId());
        try {
            dao.update(p);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update promotion: " + e.getMessage(), e);
        }
    }

    // ── 3.6.4  Disable Promotion (soft-delete) ────────────────────────────────

    public void disablePromotion(int promoId) {
        requireAdmin();
        try {
            dao.disable(promoId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to disable promotion: " + e.getMessage(), e);
        }
    }

    // ── 3.6.6  Delete Promotion (hard-delete) ─────────────────────────────────

    /**
     * Xoá cứng Promotion.
     * Điều kiện: không còn Order nào sử dụng promo này.
     */
    public void deletePromotion(int promoId) {
        requireAdmin();
        try {
            if (dao.hasLinkedOrders(promoId))
                throw new IllegalStateException(
                    "Mã khuyến mãi này đã được sử dụng trong đơn hàng. " +
                    "Không thể xoá vĩnh viễn.");
            dao.hardDelete(promoId);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Xoá khuyến mãi thất bại: " + e.getMessage(), e);
        }
    }

    // ── Employee helper: active promos for ComboBox in Order form ────────────

    public List<Promotion> getActivePromotions() {
        requireLogin();
        try {
            return dao.findActive();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load active promotions: " + e.getMessage(), e);
        }
    }

    // ── Find by ID (Admin form pre-fill) ─────────────────────────────────────

    public Promotion getById(int promoId) {
        requireAdmin();
        try {
            return dao.findById(promoId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find promotion: " + e.getMessage(), e);
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * @param p      the Promotion to validate
     * @param selfId pass -1 for INSERT, pass p.getPromoId() for UPDATE
     *               (used to exclude self when checking Code uniqueness)
     */
    private void validate(Promotion p, int selfId) {

        // Code
        if (p.getCode() == null || p.getCode().isBlank())
            throw new IllegalArgumentException("Promotion code is required.");
        if (p.getCode().trim().length() > 50)
            throw new IllegalArgumentException("Code must be ≤ 50 characters.");
        try {
            Promotion existing = dao.findByCode(p.getCode().trim());
            if (existing != null && existing.getPromoId() != selfId)
                throw new IllegalArgumentException("Promotion code \"" + p.getCode() + "\" already exists.");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception e) {
            throw new RuntimeException("DB error checking code uniqueness: " + e.getMessage(), e);
        }

        // Name
        if (p.getName() == null || p.getName().isBlank())
            throw new IllegalArgumentException("Promotion name is required.");
        if (p.getName().trim().length() > 150)
            throw new IllegalArgumentException("Name must be ≤ 150 characters.");

        // DiscountType
        if (p.getDiscountType() == null || p.getDiscountType().isBlank())
            throw new IllegalArgumentException("Discount type is required (PERCENT or FIXED).");
        if (!p.getDiscountType().equals("PERCENT") && !p.getDiscountType().equals("FIXED"))
            throw new IllegalArgumentException("Discount type must be PERCENT or FIXED.");

        // DiscountValue
        if (p.getDiscountValue() == null || p.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Discount value must be > 0.");
        if ("PERCENT".equals(p.getDiscountType())
                && p.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Percentage discount cannot exceed 100.");

        // Dates
        if (p.getStartDate() == null)
            throw new IllegalArgumentException("Start date is required.");
        if (p.getEndDate() == null)
            throw new IllegalArgumentException("End date is required.");
        if (!p.getStartDate().isBefore(p.getEndDate()))
            throw new IllegalArgumentException("Start date must be before end date.");

        // MinOrderValue
        if (p.getMinOrderValue() == null || p.getMinOrderValue().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Minimum order value must be ≥ 0.");

        // UsageLimit (nullable = unlimited)
        if (p.getUsageLimit() != null && p.getUsageLimit() <= 0)
            throw new IllegalArgumentException("Usage limit must be > 0 (or leave blank for unlimited).");

        // Status
        if (p.getStatus() == null || p.getStatus().isBlank())
            throw new IllegalArgumentException("Status is required.");
        if (!List.of("UPCOMING", "ACTIVE", "EXPIRED", "DISABLED").contains(p.getStatus()))
            throw new IllegalArgumentException("Invalid status value.");
    }
}
