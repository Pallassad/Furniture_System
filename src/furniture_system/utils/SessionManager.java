package furniture_system.utils;

import furniture_system.model.Account;
import furniture_system.model.Employee;

/**
 * SessionManager – singleton lưu trữ account và employee đang đăng nhập.
 * Phải gọi logout() khi người dùng đăng xuất.
 *
 * Lý do thêm currentEmployee:
 *   - Controller cần EmployeeId cho StockLog.ActorId và Order.EmployeeId
 *   - SessionManager chỉ lưu Account nên controller không thể lấy EmployeeId trực tiếp
 *   - Giải pháp: sau khi login xong, AuthService/LoginController gọi setCurrentEmployee()
 *     với Employee tương ứng của Account vừa đăng nhập.
 */
public final class SessionManager {

    private static SessionManager instance;

    private Account  currentAccount;
    private Employee currentEmployee;   // ← thêm mới

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    // ── Login / Logout ────────────────────────────────────────────────────

    /**
     * Lưu account sau khi xác thực thành công.
     * Gọi setCurrentEmployee() ngay sau đó để gắn Employee tương ứng.
     */
    public void login(Account account) {
        this.currentAccount = account;
    }

    /**
     * Overload tiện lợi: lưu cả account lẫn employee trong một lần gọi.
     * Dùng khi LoginController đã load Employee ngay sau xác thực.
     */
    public void login(Account account, Employee employee) {
        this.currentAccount  = account;
        this.currentEmployee = employee;
    }

    /** Xoá toàn bộ session khi đăng xuất. */
    public void logout() {
        this.currentAccount  = null;
        this.currentEmployee = null;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    /** Account đang đăng nhập, hoặc null nếu chưa đăng nhập. */
    public Account getCurrentAccount() {
        return currentAccount;
    }

    /**
     * Employee tương ứng với account đang đăng nhập.
     * Trả về null nếu chưa login hoặc chưa gọi setCurrentEmployee().
     *
     * Dùng để lấy EmployeeId cho:
     *   - Order.EmployeeId khi tạo đơn
     *   - StockLog.ActorId khi xác nhận / huỷ đơn
     */
    public Employee getCurrentEmployee() {
        return currentEmployee;
    }

    /** Gắn Employee vào session sau khi login. */
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