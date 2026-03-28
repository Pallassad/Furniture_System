package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.Employee;
import furniture_system.model.Employee.Position;
import furniture_system.model.Employee.Status;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * EmployeeDAO – CRUD + search + statistics for the Employee table.
 */
public class EmployeeDAO {

    // ── SELECT base (joined with Account for username display) ───────────────
    private static final String SELECT_BASE =
            "SELECT e.EmployeeId, e.FullName, e.Phone, e.Email, e.Address, " +
            "       e.DOB, e.Position, e.Status, e.AccountId, e.HiredAt, " +
            "       a.Username AS AccountUsername " +
            "FROM Employee e " +
            "LEFT JOIN Account a ON e.AccountId = a.AccountId ";

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    /** 3.3.1 – Returns all employees ordered by EmployeeId. */
    public List<Employee> findAll() {
        return query(SELECT_BASE + "ORDER BY e.EmployeeId", ps -> {});
    }

    /** 3.3.5 – Full-text search across name, phone, email, position, status. */
    public List<Employee> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return findAll();
        String like = "%" + keyword.trim() + "%";
        String sql  = SELECT_BASE +
                "WHERE e.FullName LIKE ? OR e.Phone LIKE ? OR e.Email LIKE ? " +
                "   OR e.Position LIKE ? OR e.Status LIKE ? " +
                "   OR CAST(e.EmployeeId AS NVARCHAR) LIKE ? " +
                "ORDER BY e.EmployeeId";
        return query(sql, ps -> {
            ps.setString(1, like); ps.setString(2, like);
            ps.setString(3, like); ps.setString(4, like);
            ps.setString(5, like); ps.setString(6, like);
        });
    }

    public Employee findById(int id) {
        List<Employee> list = query(
                SELECT_BASE + "WHERE e.EmployeeId = ?",
                ps -> ps.setInt(1, id));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Finds an Employee by AccountId — used in LoginController after
     * successful authentication to store in SessionManager.
     *
     * @param accountId  AccountId of the account that just logged in
     * @return the corresponding Employee, or null if not yet linked
     */
    public Employee findByAccountId(int accountId) {
        List<Employee> list = query(
                SELECT_BASE + "WHERE e.AccountId = ?",
                ps -> ps.setInt(1, accountId));
        return list.isEmpty() ? null : list.get(0);
    }

    /** Check if AccountId already linked to another employee (for unique constraint). */
    public boolean isAccountLinked(int accountId, int excludeEmployeeId) {
        String sql = "SELECT COUNT(*) FROM Employee WHERE AccountId = ? AND EmployeeId <> ?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, excludeEmployeeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException("isAccountLinked failed", e); }
    }

    /** Check if phone already used by another employee. */
    public boolean isPhoneDuplicate(String phone, int excludeEmployeeId) {
        String sql = "SELECT COUNT(*) FROM Employee WHERE Phone = ? AND EmployeeId <> ?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setInt(2, excludeEmployeeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException("isPhoneDuplicate failed", e); }
    }

    /** Check if email already used by another employee. */
    public boolean isEmailDuplicate(String email, int excludeEmployeeId) {
        if (email == null || email.isBlank()) return false;
        String sql = "SELECT COUNT(*) FROM Employee WHERE Email = ? AND EmployeeId <> ?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setInt(2, excludeEmployeeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException("isEmailDuplicate failed", e); }
    }

    /** Check if employee has related Orders or Salary (used for soft-delete rule). */
    public boolean hasRelatedData(int employeeId) {
        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM [Order]  WHERE EmployeeId = ?) + " +
                "(SELECT COUNT(*) FROM Salary   WHERE EmployeeId = ?) AS total";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setInt(2, employeeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt("total") > 0;
        } catch (SQLException e) { throw new RuntimeException("hasRelatedData failed", e); }
    }

    /** Returns true if the employee has any Orders (any status). */
    public boolean hasOrders(int employeeId) {
        String sql = "SELECT COUNT(*) FROM [Order] WHERE EmployeeId = ?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException("hasOrders failed", e); }
    }

    /** Returns true if the employee has any non-DRAFT Salary records. */
    public boolean hasNonDraftSalary(int employeeId) {
        String sql = "SELECT COUNT(*) FROM Salary WHERE EmployeeId = ? AND Status <> 'DRAFT'";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException("hasNonDraftSalary failed", e); }
    }

    /** Returns true if the employee has any active (non-terminal) WarrantyTickets. */
    public boolean hasActiveWarrantyTickets(int employeeId) {
        String sql = "SELECT COUNT(*) FROM WarrantyTicket " +
                "WHERE HandlerEmployeeId = ? " +
                "AND Status NOT IN ('COMPLETED','CANCELLED','REJECTED')";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException("hasActiveWarrantyTickets failed", e); }
    }

    /** Returns true if the employee has any StockLog entries. */
    public boolean hasStockLogs(int employeeId) {
        String sql = "SELECT COUNT(*) FROM StockLog WHERE ActorId = ?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException("hasStockLogs failed", e); }
    }

    /** Deletes all DRAFT Salary records for the employee (call before deleting the Employee). */
    public void deleteDraftSalaries(int employeeId) {
        String sql = "DELETE FROM Salary WHERE EmployeeId = ? AND Status = 'DRAFT'";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("deleteDraftSalaries failed", e); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE  3.3.2
    // ─────────────────────────────────────────────────────────────────────────
    public int insert(Employee e) {
        String sql = "INSERT INTO Employee (FullName, Phone, Email, Address, DOB, " +
                     "Position, Status, AccountId) " +
                     "VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindWrite(ps, e);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            return -1;
        } catch (SQLException ex) { throw new RuntimeException("insert employee failed", ex); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE  3.3.3
    // ─────────────────────────────────────────────────────────────────────────
    public boolean update(Employee e) {
        String sql = "UPDATE Employee SET FullName=?, Phone=?, Email=?, Address=?, " +
                     "DOB=?, Position=?, Status=?, AccountId=? " +
                     "WHERE EmployeeId=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bindWrite(ps, e);
            ps.setInt(9, e.getEmployeeId());
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) { throw new RuntimeException("update employee failed", ex); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATUS  3.3.4
    // ─────────────────────────────────────────────────────────────────────────
    public boolean updateStatus(int employeeId, Status newStatus) {
        String sql = "UPDATE Employee SET Status=? WHERE EmployeeId=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            ps.setInt(2, employeeId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) { throw new RuntimeException("updateStatus failed", ex); }
    }

    /** Hard delete – only allowed when no related Orders/Salary. */
    public boolean delete(int employeeId) {
        String sql = "DELETE FROM Employee WHERE EmployeeId=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) { throw new RuntimeException("delete employee failed", ex); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATISTICS
    // ─────────────────────────────────────────────────────────────────────────

    /** Employee count grouped by Position. */
    public List<Object[]> countByPosition() {
        String sql = "SELECT Position, COUNT(*) AS cnt FROM Employee GROUP BY Position ORDER BY cnt DESC";
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new Object[]{ rs.getString("Position"), rs.getInt("cnt") });
        } catch (SQLException e) { throw new RuntimeException("countByPosition failed", e); }
        return rows;
    }

    /** Employee count grouped by Status. */
    public List<Object[]> countByStatus() {
        String sql = "SELECT Status, COUNT(*) AS cnt FROM Employee GROUP BY Status ORDER BY Status";
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new Object[]{ rs.getString("Status"), rs.getInt("cnt") });
        } catch (SQLException e) { throw new RuntimeException("countByStatus failed", e); }
        return rows;
    }

    /** New employees per month (last 12 months). */
    public List<Object[]> newEmployeesByMonth() {
        String sql =
            "SELECT FORMAT(HiredAt,'yyyy-MM') AS Mon, COUNT(*) AS cnt " +
            "FROM Employee " +
            "WHERE HiredAt >= DATEADD(MONTH,-11, DATEFROMPARTS(YEAR(GETDATE()),MONTH(GETDATE()),1)) " +
            "GROUP BY FORMAT(HiredAt,'yyyy-MM') ORDER BY Mon";
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new Object[]{ rs.getString("Mon"), rs.getInt("cnt") });
        } catch (SQLException e) { throw new RuntimeException("newEmployeesByMonth failed", e); }
        return rows;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<Employee> query(String sql, Binder binder) {
        List<Employee> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("query failed", e); }
        return list;
    }

    private void bindWrite(PreparedStatement ps, Employee e) throws SQLException {
        ps.setString(1, e.getFullName());
        ps.setString(2, e.getPhone());
        ps.setString(3, (e.getEmail() == null || e.getEmail().isBlank()) ? null : e.getEmail());
        ps.setString(4, (e.getAddress() == null || e.getAddress().isBlank()) ? null : e.getAddress());
        if (e.getDob() != null)
            ps.setDate(5, Date.valueOf(e.getDob()));
        else
            ps.setNull(5, Types.DATE);
        ps.setString(6, e.getPosition().name());
        ps.setString(7, e.getStatus().name());
        ps.setInt(8, e.getAccountId());
    }

    private Employee mapRow(ResultSet rs) throws SQLException {
        Employee e = new Employee();
        e.setEmployeeId(rs.getInt("EmployeeId"));
        e.setFullName(rs.getString("FullName"));
        e.setPhone(rs.getString("Phone"));
        e.setEmail(rs.getString("Email"));
        e.setAddress(rs.getString("Address"));
        Date dob = rs.getDate("DOB");
        if (dob != null) e.setDob(dob.toLocalDate());
        e.setPosition(Position.valueOf(rs.getString("Position")));
        e.setStatus(Status.valueOf(rs.getString("Status")));
        e.setAccountId(rs.getInt("AccountId"));
        Timestamp hired = rs.getTimestamp("HiredAt");
        if (hired != null) e.setHiredAt(hired.toLocalDateTime());
        e.setAccountUsername(rs.getString("AccountUsername"));
        return e;
    }
}