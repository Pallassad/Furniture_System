package furniture_system.dao;

import furniture_system.model.Salary;
import furniture_system.config.DatabaseConfig;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-Access Object for the Salary table.
 *
 * Business rules enforced by DB:
 *  • FinalSalary is a persisted computed column — never written by app.
 *  • Unique (EmployeeId, SalaryMonth) — duplicate inserts throw SQLException.
 *  • PAID records are immutable (enforced by trigger on UPDATE).
 *  • Only DRAFT records may be hard-deleted.
 */
public class SalaryDAO {

    // ── READ ─────────────────────────────────────────────────────────────────

    /** Return all salary records with employee name. */
    public List<Salary> getAll() throws SQLException {
        String sql = """
            SELECT s.*, e.FullName AS EmployeeName
            FROM   Salary s
            JOIN   Employee e ON e.EmployeeId = s.EmployeeId
            ORDER  BY s.SalaryMonth DESC, e.FullName
            """;
        return query(sql);
    }

    /** Search by employee name, status, or month (YYYY-MM). */
    public List<Salary> search(String keyword) throws SQLException {
        String sql = """
            SELECT s.*, e.FullName AS EmployeeName
            FROM   Salary s
            JOIN   Employee e ON e.EmployeeId = s.EmployeeId
            WHERE  e.FullName LIKE ?
               OR  s.Status   LIKE ?
               OR  CAST(s.SalaryId AS NVARCHAR) LIKE ?
               OR  CONVERT(NVARCHAR, s.SalaryMonth, 120) LIKE ?
            ORDER  BY s.SalaryMonth DESC, e.FullName
            """;
        String like = "%" + keyword + "%";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) ps.setString(i, like);
            return mapResultSet(ps.executeQuery());
        }
    }

    /** Fetch a single salary record by primary key. */
    public Salary getById(int salaryId) throws SQLException {
        String sql = """
            SELECT s.*, e.FullName AS EmployeeName
            FROM   Salary s
            JOIN   Employee e ON e.EmployeeId = s.EmployeeId
            WHERE  s.SalaryId = ?
            """;
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, salaryId);
            List<Salary> list = mapResultSet(ps.executeQuery());
            return list.isEmpty() ? null : list.get(0);
        }
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Insert a new Salary record (Status defaults to DRAFT).
     * @return generated SalaryId
     */
    public int insert(Salary s) throws SQLException {
        String sql = """
            INSERT INTO Salary
                  (EmployeeId, SalaryMonth, BaseSalary, Allowance,
                   Bonus, Deduction, Status, PaidDate, Note)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getEmployeeId());
            ps.setDate(2, Date.valueOf(s.getSalaryMonth()));
            ps.setBigDecimal(3, s.getBaseSalary());
            ps.setBigDecimal(4, s.getAllowance());
            ps.setBigDecimal(5, s.getBonus());
            ps.setBigDecimal(6, s.getDeduction());
            ps.setString(7, s.getStatus() != null ? s.getStatus() : "DRAFT");
            if (s.getPaidDate() != null) ps.setDate(8, Date.valueOf(s.getPaidDate()));
            else ps.setNull(8, Types.DATE);
            setNullableString(ps, 9, s.getNote());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Full update.  The DB trigger will raise an error if Status = PAID,
     * which surfaces as a SQLException with the trigger's error message.
     */
    public int update(Salary s) throws SQLException {
        String sql = """
            UPDATE Salary
               SET EmployeeId  = ?,
                   SalaryMonth = ?,
                   BaseSalary  = ?,
                   Allowance   = ?,
                   Bonus       = ?,
                   Deduction   = ?,
                   Status      = ?,
                   PaidDate    = ?,
                   Note        = ?
             WHERE SalaryId = ?
            """;
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, s.getEmployeeId());
            ps.setDate(2, Date.valueOf(s.getSalaryMonth()));
            ps.setBigDecimal(3, s.getBaseSalary());
            ps.setBigDecimal(4, s.getAllowance());
            ps.setBigDecimal(5, s.getBonus());
            ps.setBigDecimal(6, s.getDeduction());
            ps.setString(7, s.getStatus());
            if (s.getPaidDate() != null) ps.setDate(8, Date.valueOf(s.getPaidDate()));
            else ps.setNull(8, Types.DATE);
            setNullableString(ps, 9, s.getNote());
            ps.setInt(10, s.getSalaryId());
            return ps.executeUpdate();
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-delete a DRAFT salary record.
     * The method first checks that the record is DRAFT; if not it throws.
     * The DB chk_salary_status constraint acts as a safety net.
     */
    public int delete(int salaryId) throws SQLException {
        // Guard: only allow deletion of DRAFT
        Salary existing = getById(salaryId);
        if (existing == null)
            throw new SQLException("Salary record #" + salaryId + " not found.");
        if (!"DRAFT".equals(existing.getStatus()))
            throw new SQLException("Only DRAFT salary records can be deleted. Current status: " + existing.getStatus());

        String sql = "DELETE FROM Salary WHERE SalaryId = ?";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, salaryId);
            return ps.executeUpdate();
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private List<Salary> query(String sql) throws SQLException {
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            return mapResultSet(ps.executeQuery());
        }
    }

    private List<Salary> mapResultSet(ResultSet rs) throws SQLException {
        List<Salary> list = new ArrayList<>();
        while (rs.next()) {
            Salary s = new Salary();
            s.setSalaryId(rs.getInt("SalaryId"));
            s.setEmployeeId(rs.getInt("EmployeeId"));
            Date sm = rs.getDate("SalaryMonth");
            if (sm != null) s.setSalaryMonth(sm.toLocalDate());
            s.setBaseSalary(rs.getBigDecimal("BaseSalary"));
            s.setAllowance(rs.getBigDecimal("Allowance"));
            s.setBonus(rs.getBigDecimal("Bonus"));
            s.setDeduction(rs.getBigDecimal("Deduction"));
            s.setFinalSalary(rs.getBigDecimal("FinalSalary"));
            s.setStatus(rs.getString("Status"));
            Date pd = rs.getDate("PaidDate");
            if (pd != null) s.setPaidDate(pd.toLocalDate());
            s.setNote(rs.getString("Note"));
            s.setEmployeeName(rs.getString("EmployeeName"));
            list.add(s);
        }
        return list;
    }

    private void setNullableString(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) ps.setNull(idx, Types.NVARCHAR);
        else ps.setString(idx, val);
    }
}
