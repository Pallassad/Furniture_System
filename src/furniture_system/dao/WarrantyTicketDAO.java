package furniture_system.dao;

import furniture_system.model.WarrantyTicket;
import furniture_system.config.DatabaseConfig;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-Access Object for the WarrantyTicket table.
 *
 * All mutating operations use prepared statements and let the DB
 * enforce referential-integrity / check constraints.
 */
public class WarrantyTicketDAO {

    // ── READ ─────────────────────────────────────────────────────────────────

    /** Return all tickets, joining Customer, Product, and Employee names. */
    public List<WarrantyTicket> getAll() throws SQLException {
        String sql = """
            SELECT wt.*,
                   c.FullName  AS CustomerName,
                   p.Name      AS ProductName,
                   e.FullName  AS HandlerName
            FROM   WarrantyTicket wt
            JOIN   Customer  c ON c.CustomerId  = wt.CustomerId
            JOIN   Product   p ON p.ProductId   = wt.ProductId
            LEFT JOIN Employee e ON e.EmployeeId = wt.HandlerEmployeeId
            ORDER  BY wt.CreatedAt DESC
            """;
        return query(sql);
    }

    /** Return tickets assigned to a specific employee (Employee actor). */
    public List<WarrantyTicket> getByHandler(int handlerEmployeeId) throws SQLException {
        String sql = """
            SELECT wt.*,
                   c.FullName  AS CustomerName,
                   p.Name      AS ProductName,
                   e.FullName  AS HandlerName
            FROM   WarrantyTicket wt
            JOIN   Customer  c ON c.CustomerId  = wt.CustomerId
            JOIN   Product   p ON p.ProductId   = wt.ProductId
            LEFT JOIN Employee e ON e.EmployeeId = wt.HandlerEmployeeId
            WHERE  wt.HandlerEmployeeId = ?
            ORDER  BY wt.CreatedAt DESC
            """;
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, handlerEmployeeId);
            return mapResultSet(ps.executeQuery());
        }
    }

    /**
     * Full-text search across ticket ID, customer name, product name,
     * handler name, and status.
     */
    public List<WarrantyTicket> search(String keyword) throws SQLException {
        String sql = """
            SELECT wt.*,
                   c.FullName  AS CustomerName,
                   p.Name      AS ProductName,
                   e.FullName  AS HandlerName
            FROM   WarrantyTicket wt
            JOIN   Customer  c ON c.CustomerId  = wt.CustomerId
            JOIN   Product   p ON p.ProductId   = wt.ProductId
            LEFT JOIN Employee e ON e.EmployeeId = wt.HandlerEmployeeId
            WHERE  CAST(wt.TicketId AS NVARCHAR) LIKE ?
               OR  c.FullName  LIKE ?
               OR  p.Name      LIKE ?
               OR  e.FullName  LIKE ?
               OR  wt.Status   LIKE ?
               OR  CAST(wt.OrderId AS NVARCHAR) LIKE ?
            ORDER  BY wt.CreatedAt DESC
            """;
        String like = "%" + keyword + "%";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 1; i <= 6; i++) ps.setString(i, like);
            return mapResultSet(ps.executeQuery());
        }
    }

    /** Fetch a single ticket by primary key. */
    public WarrantyTicket getById(int ticketId) throws SQLException {
        String sql = """
            SELECT wt.*,
                   c.FullName  AS CustomerName,
                   p.Name      AS ProductName,
                   e.FullName  AS HandlerName
            FROM   WarrantyTicket wt
            JOIN   Customer  c ON c.CustomerId  = wt.CustomerId
            JOIN   Product   p ON p.ProductId   = wt.ProductId
            LEFT JOIN Employee e ON e.EmployeeId = wt.HandlerEmployeeId
            WHERE  wt.TicketId = ?
            """;
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            List<WarrantyTicket> list = mapResultSet(ps.executeQuery());
            return list.isEmpty() ? null : list.get(0);
        }
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Insert a new WarrantyTicket.
     * The referenced Order MUST be in COMPLETED status — enforced by a
     * DB trigger; any violation will surface as a SQLException here.
     *
     * @return generated TicketId
     */
    public int insert(WarrantyTicket t) throws SQLException {
        String sql = """
            INSERT INTO WarrantyTicket
                  (OrderId, ProductId, CustomerId, HandlerEmployeeId,
                   IssueDesc, Status, Cost, Note, CreatedAt, UpdatedAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())
            """;
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getOrderId());
            ps.setInt(2, t.getProductId());
            ps.setInt(3, t.getCustomerId());
            setNullableInt(ps, 4, t.getHandlerEmployeeId());
            ps.setString(5, t.getIssueDesc());
            ps.setString(6, t.getStatus() != null ? t.getStatus() : "CREATED");
            ps.setBigDecimal(7, t.getCost() != null ? t.getCost() : BigDecimal.ZERO);
            setNullableString(ps, 8, t.getNote());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /** Full update of a WarrantyTicket (Admin). */
    public int update(WarrantyTicket t) throws SQLException {
        String sql = """
            UPDATE WarrantyTicket
               SET OrderId            = ?,
                   ProductId          = ?,
                   CustomerId         = ?,
                   HandlerEmployeeId  = ?,
                   IssueDesc          = ?,
                   Status             = ?,
                   Cost               = ?,
                   Note               = ?,
                   UpdatedAt          = GETDATE()
             WHERE TicketId = ?
            """;
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, t.getOrderId());
            ps.setInt(2, t.getProductId());
            ps.setInt(3, t.getCustomerId());
            setNullableInt(ps, 4, t.getHandlerEmployeeId());
            ps.setString(5, t.getIssueDesc());
            ps.setString(6, t.getStatus());
            ps.setBigDecimal(7, t.getCost());
            setNullableString(ps, 8, t.getNote());
            ps.setInt(9, t.getTicketId());
            return ps.executeUpdate();
        }
    }

    /**
     * Lightweight update for Employee: only Status, Cost, Note.
     * HandlerEmployeeId is NOT changed here (already assigned).
     */
    public int updateStatusCostNote(int ticketId, String status,
                                    BigDecimal cost, String note) throws SQLException {
        String sql = """
            UPDATE WarrantyTicket
               SET Status    = ?,
                   Cost      = ?,
                   Note      = ?,
                   UpdatedAt = GETDATE()
             WHERE TicketId  = ?
            """;
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setBigDecimal(2, cost);
            setNullableString(ps, 3, note);
            ps.setInt(4, ticketId);
            return ps.executeUpdate();
        }
    }

    // ── DELETE / CANCEL ───────────────────────────────────────────────────────

    /**
     * Soft-delete by setting Status to CANCELLED or REJECTED.
     *
     * @param newStatus must be 'CANCELLED' or 'REJECTED'
     */
    public int cancel(int ticketId, String newStatus) throws SQLException {
        if (!"CANCELLED".equals(newStatus) && !"REJECTED".equals(newStatus))
            throw new IllegalArgumentException("newStatus must be CANCELLED or REJECTED");
        String sql = "UPDATE WarrantyTicket SET Status = ?, UpdatedAt = GETDATE() WHERE TicketId = ?";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, ticketId);
            return ps.executeUpdate();
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private List<WarrantyTicket> query(String sql) throws SQLException {
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            return mapResultSet(ps.executeQuery());
        }
    }

    private List<WarrantyTicket> mapResultSet(ResultSet rs) throws SQLException {
        List<WarrantyTicket> list = new ArrayList<>();
        while (rs.next()) {
            WarrantyTicket t = new WarrantyTicket();
            t.setTicketId(rs.getInt("TicketId"));
            t.setOrderId(rs.getInt("OrderId"));
            t.setProductId(rs.getInt("ProductId"));
            t.setCustomerId(rs.getInt("CustomerId"));
            int hid = rs.getInt("HandlerEmployeeId");
            t.setHandlerEmployeeId(rs.wasNull() ? null : hid);
            t.setIssueDesc(rs.getString("IssueDesc"));
            t.setStatus(rs.getString("Status"));
            t.setCost(rs.getBigDecimal("Cost"));
            t.setNote(rs.getString("Note"));
            Timestamp ca = rs.getTimestamp("CreatedAt");
            if (ca != null) t.setCreatedAt(ca.toLocalDateTime());
            Timestamp ua = rs.getTimestamp("UpdatedAt");
            if (ua != null) t.setUpdatedAt(ua.toLocalDateTime());
            t.setCustomerName(rs.getString("CustomerName"));
            t.setProductName(rs.getString("ProductName"));
            t.setHandlerName(rs.getString("HandlerName"));
            list.add(t);
        }
        return list;
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.INTEGER);
        else ps.setInt(idx, val);
    }

    private void setNullableString(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) ps.setNull(idx, Types.NVARCHAR);
        else ps.setString(idx, val);
    }
}
