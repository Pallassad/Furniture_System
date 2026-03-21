package furniture_system.service;

import furniture_system.dao.EmployeeDAO;
import furniture_system.model.Employee;
import furniture_system.model.Employee.Status;
import furniture_system.utils.SessionManager;

import java.time.LocalDate;
import java.util.List;

/**
 * EmployeeService – business logic + validation for Employee Management.
 */
public class EmployeeService {

    private final EmployeeDAO dao = new EmployeeDAO();

    // ── Guard ─────────────────────────────────────────────────────────────────
    private void requireAdmin() {
        if (!SessionManager.getInstance().isAdmin())
            throw new SecurityException("Access denied: Admin only.");
    }

    // ── 3.3.1 View ────────────────────────────────────────────────────────────
    public List<Employee> getAll() {
        requireAdmin();
        return dao.findAll();
    }

    // ── 3.3.5 Search ──────────────────────────────────────────────────────────
    public List<Employee> search(String keyword) {
        requireAdmin();
        return dao.search(keyword);
    }

    // ── 3.3.2 Add ─────────────────────────────────────────────────────────────
    public int addEmployee(Employee e) {
        requireAdmin();
        validate(e, -1);
        return dao.insert(e);
    }

    // ── 3.3.3 Update ──────────────────────────────────────────────────────────
    public boolean updateEmployee(Employee e) {
        requireAdmin();
        validate(e, e.getEmployeeId());
        return dao.update(e);
    }

    // ── 3.3.4 Delete / Deactivate ─────────────────────────────────────────────
    /**
     * Soft-delete: sets Status = INACTIVE.
     * Hard-delete: only if no related Orders or Salary records.
     */
    public String removeEmployee(int employeeId) {
        requireAdmin();
        if (dao.hasRelatedData(employeeId)) {
            dao.updateStatus(employeeId, Status.INACTIVE);
            return "SOFT_DELETED"; // caller shows appropriate message
        } else {
            dao.delete(employeeId);
            return "HARD_DELETED";
        }
    }

    public boolean updateStatus(int employeeId, Status newStatus) {
        requireAdmin();
        return dao.updateStatus(employeeId, newStatus);
    }

    // ── Statistics ────────────────────────────────────────────────────────────
    public List<Object[]> countByPosition()    { requireAdmin(); return dao.countByPosition(); }
    public List<Object[]> countByStatus()      { requireAdmin(); return dao.countByStatus(); }
    public List<Object[]> newEmployeesByMonth(){ requireAdmin(); return dao.newEmployeesByMonth(); }

    // ── Validation ────────────────────────────────────────────────────────────
    private void validate(Employee e, int selfId) {
        if (e.getFullName() == null || e.getFullName().isBlank())
            throw new IllegalArgumentException("Full name is required.");
        if (e.getFullName().length() > 100)
            throw new IllegalArgumentException("Full name must be ≤ 100 characters.");

        if (e.getPhone() == null || e.getPhone().isBlank())
            throw new IllegalArgumentException("Phone is required.");
        if (!e.getPhone().matches("\\d{9,11}"))
            throw new IllegalArgumentException("Phone must be 9–11 digits (numbers only).");
        if (dao.isPhoneDuplicate(e.getPhone(), selfId))
            throw new IllegalArgumentException("Phone number already exists.");

        if (e.getEmail() != null && !e.getEmail().isBlank()) {
            if (!e.getEmail().matches(".*@.*\\..*"))
                throw new IllegalArgumentException("Invalid email format.");
            if (dao.isEmailDuplicate(e.getEmail(), selfId))
                throw new IllegalArgumentException("Email already exists.");
        }

        if (e.getDob() != null && !e.getDob().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Date of birth must be before today.");

        if (e.getPosition() == null)
            throw new IllegalArgumentException("Position is required.");
        if (e.getStatus() == null)
            throw new IllegalArgumentException("Status is required.");

        if (e.getAccountId() <= 0)
            throw new IllegalArgumentException("A linked Account must be selected.");
        if (dao.isAccountLinked(e.getAccountId(), selfId))
            throw new IllegalArgumentException("This account is already linked to another employee.");
    }
}
