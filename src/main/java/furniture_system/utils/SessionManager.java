package furniture_system.utils;

import furniture_system.model.Account;
import furniture_system.model.Employee;

/**
 * SessionManager – singleton that stores the currently logged-in account and employee.
 * Call logout() when the user signs out.
 *
 * Why currentEmployee was added:
 *   - Controllers need EmployeeId for StockLog.ActorId and Order.EmployeeId
 *   - SessionManager stored only Account, so controllers could not retrieve EmployeeId directly
 *   - Fix: after login, AuthService/LoginController calls setCurrentEmployee()
 *     with the Employee that corresponds to the account just logged in.
 */
public final class SessionManager {

    private static SessionManager instance;

    private Account  currentAccount;
    private Employee currentEmployee;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    // ── Login / Logout ────────────────────────────────────────────────────

    /**
     * Stores the account after successful authentication.
     * Call setCurrentEmployee() immediately after to attach the corresponding Employee.
     */
    public void login(Account account) {
        this.currentAccount = account;
    }

    /**
     * Convenience overload: stores both account and employee in one call.
     * Use when LoginController has already loaded the Employee after authentication.
     */
    public void login(Account account, Employee employee) {
        this.currentAccount  = account;
        this.currentEmployee = employee;
    }

    /** Clears the entire session on logout. */
    public void logout() {
        this.currentAccount  = null;
        this.currentEmployee = null;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    /** The currently logged-in account, or null if not logged in. */
    public Account getCurrentAccount() {
        return currentAccount;
    }

    /**
     * The Employee corresponding to the currently logged-in account.
     * Returns null if not logged in or setCurrentEmployee() has not been called.
     *
     * Used to retrieve EmployeeId for:
     *   - Order.EmployeeId when creating an order
     *   - StockLog.ActorId when confirming or cancelling an order
     */
    public Employee getCurrentEmployee() {
        return currentEmployee;
    }

    /** Attaches an Employee to the session after login. */
    public void setCurrentEmployee(Employee employee) {
        this.currentEmployee = employee;
    }

    // ── Helper checks ─────────────────────────────────────────────────────

    public boolean isLoggedIn() {
        return currentAccount != null;
    }

    public boolean isAdmin() {
        return isLoggedIn() && currentAccount.isAdmin();
    }
}