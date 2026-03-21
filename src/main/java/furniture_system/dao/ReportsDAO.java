package furniture_system.dao;

import furniture_system.config.DatabaseConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ReportsDAO – aggregates all statistical queries for the Admin Summary Reports.
 * Covers: Orders, Customers, Products & Stock, Salary, Warranty.
 */
public class ReportsDAO {

    // ══════════════════════════════════════════════════════════════════════
    //  ORDERS
    // ══════════════════════════════════════════════════════════════════════

    /** Total revenue (FinalTotal of COMPLETED orders). */
    public double getTotalRevenue() throws SQLException {
        String sql = "SELECT ISNULL(SUM(FinalTotal), 0) FROM [Order] WHERE Status = 'COMPLETED'";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    /** Total number of orders. */
    public int getTotalOrders() throws SQLException {
        String sql = "SELECT COUNT(*) FROM [Order]";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Orders by status: [Status, Count]. */
    public List<Object[]> getOrdersByStatus() throws SQLException {
        String sql = "SELECT Status, COUNT(*) FROM [Order] GROUP BY Status ORDER BY COUNT(*) DESC";
        return queryRows(sql);
    }

    /** Monthly revenue (last 12 months): [Month, Revenue]. */
    public List<Object[]> getRevenueByMonth() throws SQLException {
        String sql =
            "SELECT FORMAT(OrderDate,'yyyy-MM') AS Mon, ISNULL(SUM(FinalTotal),0) " +
            "FROM [Order] WHERE Status='COMPLETED' " +
            "  AND OrderDate >= DATEADD(MONTH,-11,DATEFROMPARTS(YEAR(GETDATE()),MONTH(GETDATE()),1)) " +
            "GROUP BY FORMAT(OrderDate,'yyyy-MM') ORDER BY Mon";
        return queryRows(sql);
    }

    /** Top 5 employees by sales: [EmployeeName, Total]. */
    public List<Object[]> getTopEmployeesBySales() throws SQLException {
        String sql =
            "SELECT TOP 5 e.FullName, ISNULL(SUM(o.FinalTotal),0) AS Total " +
            "FROM [Order] o JOIN Employee e ON e.EmployeeId = o.EmployeeId " +
            "WHERE o.Status = 'COMPLETED' " +
            "GROUP BY e.EmployeeId, e.FullName ORDER BY Total DESC";
        return queryRows(sql);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CUSTOMERS
    // ══════════════════════════════════════════════════════════════════════

    /** Total number of customers. */
    public int getTotalCustomers() throws SQLException {
        String sql = "SELECT COUNT(*) FROM Customer";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Customers by status: [Status, Count]. */
    public List<Object[]> getCustomersByStatus() throws SQLException {
        String sql = "SELECT Status, COUNT(*) FROM Customer GROUP BY Status";
        return queryRows(sql);
    }

    /** Top 5 customers by spending: [Name, Total]. */
    public List<Object[]> getTopSpenders() throws SQLException {
        String sql =
            "SELECT TOP 5 c.FullName, SUM(o.FinalTotal) AS Total " +
            "FROM [Order] o JOIN Customer c ON c.CustomerId = o.CustomerId " +
            "WHERE o.Status NOT IN ('CANCELLED','RETURNED') " +
            "GROUP BY c.CustomerId, c.FullName ORDER BY Total DESC";
        return queryRows(sql);
    }

    /** New customers by month (last 12 months): [Month, Count]. */
    public List<Object[]> getNewCustomersByMonth() throws SQLException {
        String sql =
            "SELECT FORMAT(CreatedAt,'yyyy-MM') AS Mon, COUNT(*) " +
            "FROM Customer " +
            "WHERE CreatedAt >= DATEADD(MONTH,-11,DATEFROMPARTS(YEAR(GETDATE()),MONTH(GETDATE()),1)) " +
            "GROUP BY FORMAT(CreatedAt,'yyyy-MM') ORDER BY Mon";
        return queryRows(sql);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PRODUCTS & STOCK
    // ══════════════════════════════════════════════════════════════════════

    /** Total number of products. */
    public int getTotalProducts() throws SQLException {
        String sql = "SELECT COUNT(*) FROM Product";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Products by status: [Status, Count]. */
    public List<Object[]> getProductsByStatus() throws SQLException {
        String sql = "SELECT Status, COUNT(*) FROM Product GROUP BY Status ORDER BY COUNT(*) DESC";
        return queryRows(sql);
    }

    /** Top 10 low stock products (below ReorderLevel): [ProductName, Quantity, ReorderLevel]. */
    public List<Object[]> getLowStockProducts() throws SQLException {
        String sql =
            "SELECT TOP 10 p.Name, s.Quantity, s.ReorderLevel " +
            "FROM Stock s JOIN Product p ON p.ProductId = s.ProductId " +
            "WHERE s.Quantity <= s.ReorderLevel " +
            "ORDER BY s.Quantity ASC";
        return queryRows(sql);
    }

    /** Top 5 best-selling products: [ProductName, TotalSold]. */
    public List<Object[]> getBestSellingProducts() throws SQLException {
        String sql =
            "SELECT TOP 5 p.Name, SUM(ol.Quantity) AS TotalSold " +
            "FROM OrderLine ol JOIN Product p ON p.ProductId = ol.ProductId " +
            "JOIN [Order] o ON o.OrderId = ol.OrderId " +
            "WHERE o.Status = 'COMPLETED' " +
            "GROUP BY p.ProductId, p.Name ORDER BY TotalSold DESC";
        return queryRows(sql);
    }

    /** Total stock value (Quantity * Price). */
    public double getTotalStockValue() throws SQLException {
        String sql =
            "SELECT ISNULL(SUM(s.Quantity * p.Price), 0) " +
            "FROM Stock s JOIN Product p ON p.ProductId = s.ProductId";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SALARY
    // ══════════════════════════════════════════════════════════════════════

    /** Total salary paid (PAID records). */
    public double getTotalSalaryPaid() throws SQLException {
        String sql = "SELECT ISNULL(SUM(FinalSalary),0) FROM Salary WHERE Status='PAID'";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    /** Salary records by status: [Status, Count]. */
    public List<Object[]> getSalaryByStatus() throws SQLException {
        String sql = "SELECT Status, COUNT(*) FROM Salary GROUP BY Status";
        return queryRows(sql);
    }

    /** Top 5 highest salaries this month: [EmployeeName, FinalSalary]. */
    public List<Object[]> getTopSalaryThisMonth() throws SQLException {
        String sql =
            "SELECT TOP 5 e.FullName, s.FinalSalary " +
            "FROM Salary s JOIN Employee e ON e.EmployeeId = s.EmployeeId " +
            "WHERE s.SalaryMonth = DATEFROMPARTS(YEAR(GETDATE()), MONTH(GETDATE()), 1) " +
            "ORDER BY s.FinalSalary DESC";
        return queryRows(sql);
    }

    /** Monthly salary cost (last 12 months): [Month, Total]. */
    public List<Object[]> getSalaryByMonth() throws SQLException {
        String sql =
            "SELECT FORMAT(SalaryMonth,'yyyy-MM') AS Mon, ISNULL(SUM(FinalSalary),0) " +
            "FROM Salary WHERE Status='PAID' " +
            "  AND SalaryMonth >= DATEADD(MONTH,-11,DATEFROMPARTS(YEAR(GETDATE()),MONTH(GETDATE()),1)) " +
            "GROUP BY FORMAT(SalaryMonth,'yyyy-MM') ORDER BY Mon";
        return queryRows(sql);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  WARRANTY
    // ══════════════════════════════════════════════════════════════════════

    /** Total warranty tickets. */
    public int getTotalWarrantyTickets() throws SQLException {
        String sql = "SELECT COUNT(*) FROM WarrantyTicket";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Tickets by status: [Status, Count]. */
    public List<Object[]> getWarrantyByStatus() throws SQLException {
        String sql = "SELECT Status, COUNT(*) FROM WarrantyTicket GROUP BY Status ORDER BY COUNT(*) DESC";
        return queryRows(sql);
    }

    /** Total warranty cost. */
    public double getTotalWarrantyCost() throws SQLException {
        String sql = "SELECT ISNULL(SUM(Cost),0) FROM WarrantyTicket WHERE Status NOT IN ('CANCELLED','REJECTED')";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    /** Top 5 most warranted products: [ProductName, Count]. */
    public List<Object[]> getMostWarrantiedProducts() throws SQLException {
        String sql =
            "SELECT TOP 5 p.Name, COUNT(*) AS Cnt " +
            "FROM WarrantyTicket wt JOIN Product p ON p.ProductId = wt.ProductId " +
            "GROUP BY p.ProductId, p.Name ORDER BY Cnt DESC";
        return queryRows(sql);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPER
    // ══════════════════════════════════════════════════════════════════════

    private List<Object[]> queryRows(String sql) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Object[] row = new Object[cols];
                for (int i = 0; i < cols; i++) row[i] = rs.getObject(i + 1);
                rows.add(row);
            }
        }
        return rows;
    }
}