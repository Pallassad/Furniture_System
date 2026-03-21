package furniture_system.controller;

import furniture_system.dao.ReportsDAO;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * AdminReportsController – displays the Admin Summary Report.
 * Layout: 5 section cards (Orders, Customers, Products & Stock, Salary, Warranty).
 */
public class AdminReportsController {

    @FXML private ScrollPane scrollPane;
    @FXML private VBox       rootVBox;

    private final ReportsDAO dao = new ReportsDAO();
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    @FXML
    public void initialize() {
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color:#f5f6fa; -fx-background:#f5f6fa;");
        rootVBox.setStyle("-fx-background-color:#f5f6fa;");
        rootVBox.setPadding(new Insets(20));
        rootVBox.setSpacing(20);
        buildUI();
    }

    private void buildUI() {
        rootVBox.getChildren().clear();
        rootVBox.getChildren().add(buildPageHeader());
        rootVBox.getChildren().add(buildOrdersSection());
        rootVBox.getChildren().add(buildCustomersSection());
        rootVBox.getChildren().add(buildStockSection());
        rootVBox.getChildren().add(buildSalarySection());
        rootVBox.getChildren().add(buildWarrantySection());
    }

    // ── Page header ──────────────────────────────────────────────────────────

    private HBox buildPageHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 20, 14, 20));
        header.setStyle("-fx-background-color:#1a237e; -fx-background-radius:8;");

        Label icon  = styledLabel("📊", 26, "white", true);
        Label title = styledLabel("Summary Reports", 20, "white", true);
        Label sub   = styledLabel("Full system overview – real-time data", 13, "#c5cae9", false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnRefresh = new Button("🔄  Refresh");
        btnRefresh.setStyle(
            "-fx-background-color:#3949ab; -fx-text-fill:white;" +
            "-fx-background-radius:6; -fx-cursor:hand; -fx-padding:8 16; -fx-font-weight:bold;"
        );
        btnRefresh.setOnAction(e -> buildUI());

        VBox titleBox = new VBox(2, title, sub);
        header.getChildren().addAll(icon, titleBox, spacer, btnRefresh);
        return header;
    }

    // ── Orders ───────────────────────────────────────────────────────────────

    private VBox buildOrdersSection() {
        VBox section = createSection("🛒  Orders – Revenue & Status", "#1565c0");
        try {
            double         revenue  = dao.getTotalRevenue();
            int            total    = dao.getTotalOrders();
            List<Object[]> byStatus = dao.getOrdersByStatus();
            List<Object[]> byMonth  = dao.getRevenueByMonth();
            List<Object[]> topEmp   = dao.getTopEmployeesBySales();

            HBox kpi = new HBox(12);
            kpi.getChildren().addAll(
                kpiCard("💰 Revenue (COMPLETED)", formatNum(revenue) + " VND", "#1565c0"),
                kpiCard("📋 Total Orders", String.valueOf(total), "#283593")
            );
            section.getChildren().add(kpi);

            HBox row = new HBox(12);
            row.getChildren().addAll(
                tableCard("Orders by Status", byStatus, new String[]{"Status", "Count"}),
                tableCard("Top 5 Employees by Sales", topEmp, new String[]{"Employee", "Revenue (VND)"})
            );
            section.getChildren().add(row);
            section.getChildren().add(
                tableCard("Monthly Revenue (last 12 months)", byMonth, new String[]{"Month", "Revenue (VND)"})
            );
        } catch (SQLException e) { section.getChildren().add(errorLabel(e.getMessage())); }
        return section;
    }

    // ── Customers ────────────────────────────────────────────────────────────

    private VBox buildCustomersSection() {
        VBox section = createSection("👤  Customers", "#00695c");
        try {
            int            total    = dao.getTotalCustomers();
            List<Object[]> byStatus = dao.getCustomersByStatus();
            List<Object[]> topSpend = dao.getTopSpenders();
            List<Object[]> newByMon = dao.getNewCustomersByMonth();

            HBox kpi = new HBox(12);
            kpi.getChildren().add(kpiCard("👥 Total Customers", String.valueOf(total), "#00695c"));
            section.getChildren().add(kpi);

            HBox row = new HBox(12);
            row.getChildren().addAll(
                tableCard("Customers by Status", byStatus, new String[]{"Status", "Count"}),
                tableCard("Top 5 Customers by Spending", topSpend, new String[]{"Customer", "Total Spent (VND)"})
            );
            section.getChildren().add(row);
            section.getChildren().add(
                tableCard("New Customers by Month (last 12 months)", newByMon, new String[]{"Month", "New Customers"})
            );
        } catch (SQLException e) { section.getChildren().add(errorLabel(e.getMessage())); }
        return section;
    }

    // ── Products & Stock ─────────────────────────────────────────────────────

    private VBox buildStockSection() {
        VBox section = createSection("📦  Products & Stock", "#4527a0");
        try {
            int            totalProd = dao.getTotalProducts();
            double         stockVal  = dao.getTotalStockValue();
            List<Object[]> byStatus  = dao.getProductsByStatus();
            List<Object[]> lowStock  = dao.getLowStockProducts();
            List<Object[]> bestSell  = dao.getBestSellingProducts();

            HBox kpi = new HBox(12);
            kpi.getChildren().addAll(
                kpiCard("📦 Total Products",    String.valueOf(totalProd),    "#4527a0"),
                kpiCard("💹 Total Stock Value", formatNum(stockVal) + " VND", "#6a1b9a")
            );
            section.getChildren().add(kpi);

            HBox row = new HBox(12);
            row.getChildren().addAll(
                tableCard("Products by Status", byStatus, new String[]{"Status", "Count"}),
                tableCard("Top 5 Best-Selling Products", bestSell, new String[]{"Product", "Units Sold"})
            );
            section.getChildren().add(row);

            VBox lowCard = tableCard(
                "⚠  Low Stock Alert (≤ Reorder Level)", lowStock,
                new String[]{"Product", "Stock Qty", "Reorder Level"}
            );
            lowCard.setStyle(lowCard.getStyle() + "-fx-border-color:#ef6c00; -fx-border-width:0 0 0 4;");
            section.getChildren().add(lowCard);
        } catch (SQLException e) { section.getChildren().add(errorLabel(e.getMessage())); }
        return section;
    }

    // ── Salary ───────────────────────────────────────────────────────────────

    private VBox buildSalarySection() {
        VBox section = createSection("💰  Salary", "#b71c1c");
        try {
            double         totalPaid = dao.getTotalSalaryPaid();
            List<Object[]> byStatus  = dao.getSalaryByStatus();
            List<Object[]> topSal    = dao.getTopSalaryThisMonth();
            List<Object[]> byMonth   = dao.getSalaryByMonth();

            HBox kpi = new HBox(12);
            kpi.getChildren().add(kpiCard("💸 Total Salary Paid (PAID)", formatNum(totalPaid) + " VND", "#b71c1c"));
            section.getChildren().add(kpi);

            HBox row = new HBox(12);
            row.getChildren().addAll(
                tableCard("Salary Records by Status", byStatus, new String[]{"Status", "Count"}),
                tableCard("Top 5 Salaries This Month", topSal, new String[]{"Employee", "Final Salary (VND)"})
            );
            section.getChildren().add(row);
            section.getChildren().add(
                tableCard("Monthly Salary Cost (last 12 months)", byMonth, new String[]{"Month", "Total Salary (VND)"})
            );
        } catch (SQLException e) { section.getChildren().add(errorLabel(e.getMessage())); }
        return section;
    }

    // ── Warranty ─────────────────────────────────────────────────────────────

    private VBox buildWarrantySection() {
        VBox section = createSection("🔧  Warranty & Repair", "#37474f");
        try {
            int            total    = dao.getTotalWarrantyTickets();
            double         cost     = dao.getTotalWarrantyCost();
            List<Object[]> byStatus = dao.getWarrantyByStatus();
            List<Object[]> topProd  = dao.getMostWarrantiedProducts();

            HBox kpi = new HBox(12);
            kpi.getChildren().addAll(
                kpiCard("🔧 Total Warranty Tickets", String.valueOf(total),    "#37474f"),
                kpiCard("💵 Total Warranty Cost",    formatNum(cost) + " VND", "#455a64")
            );
            section.getChildren().add(kpi);

            HBox row = new HBox(12);
            row.getChildren().addAll(
                tableCard("Tickets by Status", byStatus, new String[]{"Status", "Count"}),
                tableCard("Top 5 Most Warranted Products", topProd, new String[]{"Product", "Warranty Count"})
            );
            section.getChildren().add(row);
        } catch (SQLException e) { section.getChildren().add(errorLabel(e.getMessage())); }
        return section;
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private VBox createSection(String title, String accent) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setStyle(
            "-fx-background-color:white; -fx-background-radius:8; -fx-border-radius:8;" +
            "-fx-border-width:0 0 0 4; -fx-border-color:" + accent + ";" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.06),6,0,0,2);"
        );
        Label lbl = new Label(title);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        lbl.setStyle("-fx-text-fill:" + accent + ";");
        Separator sep = new Separator();
        box.getChildren().addAll(lbl, sep);
        return box;
    }

    private VBox kpiCard(String label, String value, String color) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
            "-fx-background-color:white; -fx-background-radius:8;" +
            "-fx-border-color:" + color + "; -fx-border-radius:8;" +
            "-fx-border-width:0 0 0 4;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.07),4,0,0,2);"
        );
        HBox.setHgrow(card, Priority.ALWAYS);
        Label lblTitle = new Label(label);
        lblTitle.setStyle("-fx-font-size:12px; -fx-text-fill:#757575;");
        Label lblValue = new Label(value);
        lblValue.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        lblValue.setStyle("-fx-text-fill:" + color + ";");
        card.getChildren().addAll(lblTitle, lblValue);
        return card;
    }

    private VBox tableCard(String title, List<Object[]> rows, String[] headers) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle(
            "-fx-background-color:#fafafa; -fx-background-radius:6;" +
            "-fx-border-color:#e0e0e0; -fx-border-radius:6;"
        );
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lbl = new Label(title);
        lbl.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#37474f;");
        card.getChildren().add(lbl);

        GridPane grid = new GridPane();
        for (int c = 0; c < headers.length; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
            Label h = new Label(headers[c]);
            h.setStyle("-fx-font-weight:bold; -fx-font-size:12px; -fx-text-fill:#1a237e;" +
                       "-fx-background-color:#e8eaf6; -fx-padding:6 10;");
            h.setMaxWidth(Double.MAX_VALUE);
            GridPane.setFillWidth(h, true);
            grid.add(h, c, 0);
        }

        if (rows.isEmpty()) {
            Label empty = new Label("No data available");
            empty.setStyle("-fx-text-fill:#9e9e9e; -fx-font-style:italic; -fx-padding:8 10;");
            grid.add(empty, 0, 1);
            GridPane.setColumnSpan(empty, headers.length);
        } else {
            for (int r = 0; r < rows.size(); r++) {
                Object[] row = rows.get(r);
                String bg = (r % 2 == 0) ? "white" : "#fafafa";
                for (int c = 0; c < Math.min(row.length, headers.length); c++) {
                    String raw = row[c] == null ? "-" : row[c].toString();
                    String display = raw;
                    try {
                        double num = Double.parseDouble(raw);
                        if (num > 999 && (headers[c].contains("VND") || headers[c].contains("Revenue") ||
                            headers[c].contains("Salary") || headers[c].contains("Spent") ||
                            headers[c].contains("Cost"))) {
                            display = formatNum(num);
                        }
                    } catch (NumberFormatException ignored) {}
                    Label cell = new Label(display);
                    cell.setStyle("-fx-font-size:12px; -fx-text-fill:#37474f;" +
                                  "-fx-padding:6 10; -fx-background-color:" + bg + ";");
                    cell.setMaxWidth(Double.MAX_VALUE);
                    GridPane.setFillWidth(cell, true);
                    grid.add(cell, c, r + 1);
                }
            }
        }
        card.getChildren().add(grid);
        return card;
    }

    private Label styledLabel(String text, double size, String color, boolean bold) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("Segoe UI", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        lbl.setStyle("-fx-text-fill:" + color + ";");
        return lbl;
    }

    private Label errorLabel(String msg) {
        Label lbl = new Label("⚠ Failed to load data: " + msg);
        lbl.setStyle("-fx-text-fill:#c62828; -fx-font-size:12px; -fx-padding:8;");
        return lbl;
    }

    private String formatNum(double value) {
        return NUMBER_FORMAT.format((long) value);
    }
}