package furniture_system.service;

import furniture_system.dao.CustomerDAO;
import furniture_system.model.Customer;
import furniture_system.model.Customer.Status;
import furniture_system.model.OrderSummary;
import furniture_system.utils.SessionManager;

import java.util.List;

/**
 * CustomerService – business logic + validation for Customer Management.
 * Admin : full CRUD + statistics.
 * Employee : search + create (Status=ACTIVE) + view purchase history.
 */
public class CustomerService {

    private final CustomerDAO dao = new CustomerDAO();

    // ── Guards ────────────────────────────────────────────────────────────────
    private void requireLogin() {
        if (!SessionManager.getInstance().isLoggedIn())
            throw new SecurityException("You must be logged in.");
    }
    private void requireAdmin() {
        if (!SessionManager.getInstance().isAdmin())
            throw new SecurityException("Access denied: Admin only.");
    }

    // ── 3.10.1 / 4.9.1  View & Search ────────────────────────────────────────
    public List<Customer> getAll() {
        requireLogin();
        return dao.findAll();
    }

    public List<Customer> search(String keyword) {
        requireLogin();
        return dao.search(keyword);
    }

    // ── 3.10.2  Admin: Add Customer ───────────────────────────────────────────
    public int addCustomer(Customer c) {
        requireAdmin();
        validate(c, -1);
        return dao.insert(c);
    }

    // ── 4.9.2  Employee: Create Customer (Status always ACTIVE) ──────────────
    public int createCustomer(Customer c) {
        requireLogin();
        c.setStatus(Status.ACTIVE); // employees cannot set status
        validate(c, -1);
        return dao.insert(c);
    }

    // ── 3.10.3  Update ────────────────────────────────────────────────────────
    public boolean updateCustomer(Customer c) {
        requireAdmin();
        validate(c, c.getCustomerId());
        return dao.update(c);
    }

    // ── 3.10.4  Delete / Deactivate ───────────────────────────────────────────
    /**
     * Xoá khách hàng:
     *  - Nếu còn Order → báo lỗi, không xoá (phải xoá Order trước)
     *  - Nếu không còn Order → hard-delete (DeliveryAddress cascade theo FK hoặc xoá thủ công)
     */
    public String removeCustomer(int customerId) {
        requireAdmin();
        if (dao.hasOrders(customerId))
            throw new IllegalStateException(
                "Khách hàng này còn đơn hàng. Vui lòng xoá tất cả đơn hàng của khách hàng trước.");
        // Xoá DeliveryAddress không còn liên kết Order
        dao.deleteUnlinkedAddresses(customerId);
        dao.delete(customerId);
        return "HARD";
    }

    public boolean updateStatus(int customerId, Status newStatus) {
        requireAdmin();
        return dao.updateStatus(customerId, newStatus);
    }

    // ── 4.9.3  Purchase History ───────────────────────────────────────────────
    public List<OrderSummary> getPurchaseHistory(int customerId) {
        requireLogin();
        return dao.getPurchaseHistory(customerId);
    }

    // ── Statistics (Admin only) ───────────────────────────────────────────────
    public List<Object[]> countByStatus()  { requireAdmin(); return dao.countByStatus(); }
    public List<Object[]> countByGender()  { requireAdmin(); return dao.countByGender(); }
    public List<Object[]> newByMonth()     { requireAdmin(); return dao.newByMonth(); }
    public List<Object[]> topSpenders()    { requireAdmin(); return dao.topSpenders(); }

    // ── Validation ────────────────────────────────────────────────────────────
    private void validate(Customer c, int selfId) {
        if (c.getFullName() == null || c.getFullName().isBlank())
            throw new IllegalArgumentException("Full name is required.");
        if (c.getFullName().trim().length() < 2)
            throw new IllegalArgumentException("Full name must be at least 2 characters.");
        if (c.getFullName().length() > 100)
            throw new IllegalArgumentException("Full name must be ≤ 100 characters.");

        if (c.getPhone() == null || c.getPhone().isBlank())
            throw new IllegalArgumentException("Phone is required.");
        if (!c.getPhone().matches("\\d{9,11}"))
            throw new IllegalArgumentException("Phone must contain 9–11 digits only.");
        if (dao.isPhoneDuplicate(c.getPhone(), selfId))
            throw new IllegalArgumentException("Phone number already exists.");

        if (c.getEmail() != null && !c.getEmail().isBlank()) {
            if (!c.getEmail().matches(".*@.*\\..*"))
                throw new IllegalArgumentException("Invalid email format.");
            if (dao.isEmailDuplicate(c.getEmail(), selfId))
                throw new IllegalArgumentException("Email already exists.");
        }

        if (c.getStatus() == null)
            throw new IllegalArgumentException("Status is required.");
    }
}
