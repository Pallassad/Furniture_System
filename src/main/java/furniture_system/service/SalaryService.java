package furniture_system.service;

import furniture_system.dao.SalaryDAO;
import furniture_system.model.Salary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * SalaryService – Business logic layer for the Salary module.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 *  HOW TO INPUT BONUS (Reward / Deduction):
 *
 *  Fixed amount:
 *    500000  or  +500000  →  Reward +500.000 ₫
 *    -200000                →  Deduction -200.000 ₫
 *
 *  As % of base salary:
 *    10%   or  +10%       →  Reward 10% × BaseSalary
 *    -5%                  →  Deduction 5% × BaseSalary
 *
 *  Service auto-splits when saving to DB:
 *    bonus > 0  →  DB.Bonus = bonus,   DB.Deduction = 0
 *    bonus < 0  →  DB.Bonus = 0,       DB.Deduction = |bonus|
 *    bonus = 0  →  DB.Bonus = 0,       DB.Deduction = 0
 * └─────────────────────────────────────────────────────────────┘
 */
public class SalaryService {

    private final SalaryDAO dao = new SalaryDAO();

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    public List<Salary> getAll() throws SQLException {
        return dao.getAll();
    }

    public List<Salary> search(String keyword) throws SQLException {
        if (keyword == null || keyword.isBlank()) return dao.getAll();
        return dao.search(keyword);
    }

    public Salary getById(int salaryId) throws SQLException {
        return dao.getById(salaryId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Add a new salary record.
     *
     * @param allowanceInput  Allowance — number ≥ 0 or "X%"
     * @param bonusInput      Reward/Deduction — can be negative: "500000", "-200000", "10%", "-5%"
     */
    public int addSalary(int employeeId,
                         LocalDate month,
                         BigDecimal baseSalary,
                         String allowanceInput,
                         String bonusInput,
                         String status,
                         LocalDate paidDate,
                         String note) throws SQLException {

        Salary s = buildSalary(0, employeeId, month, baseSalary,
                allowanceInput, bonusInput, status, paidDate, note);
        return dao.insert(s);
    }

    /**
     * Update a salary record.
     * Guard: PAID records cannot be edited.
     */
    public void updateSalary(int salaryId,
                             int employeeId,
                             LocalDate month,
                             BigDecimal baseSalary,
                             String allowanceInput,
                             String bonusInput,
                             String status,
                             LocalDate paidDate,
                             String note) throws SQLException {

        Salary existing = dao.getById(salaryId);
        if (existing == null)
            throw new IllegalArgumentException("Record #" + salaryId + " not found.");
        if ("PAID".equals(existing.getStatus()))
            throw new IllegalArgumentException("Cannot edit a record that is PAID.");

        Salary s = buildSalary(salaryId, employeeId, month, baseSalary,
                allowanceInput, bonusInput, status, paidDate, note);
        dao.update(s);
    }

    /** Delete — DRAFT only. */
    public void deleteSalary(int salaryId) throws SQLException {
        dao.delete(salaryId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PARSING  (public — Controller uses for live preview)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parse input string into signed number (signed BigDecimal).
     *
     * Accepted formats:
     *   "500000"   →  +500.000   (reward)
     *   "+500000"  →  +500.000   (explicit reward)
     *   "-200000"  →  -200.000   (deduction)
     *   "10%"      →  +10% × base
     *   "+10%"     →  +10% × base
     *   "-5%"      →  - 5% × base  (deduction)
     *   ""  / null →  0
     *
     * @param input         User input string
     * @param baseSalary    Base salary (used when calculating %)
     * @param allowNegative true  = accept negative (Bonus/Deduction)
     *                      false = accept only ≥ 0 (Allowance)
     */
    public BigDecimal parseAmount(String input, BigDecimal baseSalary, boolean allowNegative) {
        if (input == null || input.isBlank()) return BigDecimal.ZERO;

        String trimmed = input.strip();

        // ── Extract leading sign ─────────────────────────────────────────────
        boolean negative = false;
        if (trimmed.startsWith("-")) {
            negative = true;
            trimmed  = trimmed.substring(1).strip();
        } else if (trimmed.startsWith("+")) {
            trimmed  = trimmed.substring(1).strip();
        }

        if (trimmed.isEmpty())
            throw new IllegalArgumentException("Empty value. Enter amount or percentage.");

        BigDecimal absValue;

        // ── Percentage ───────────────────────────────────────────────────────
        if (trimmed.endsWith("%")) {
            String pctStr = trimmed.substring(0, trimmed.length() - 1).strip();
            if (pctStr.isEmpty())
                throw new IllegalArgumentException("Missing number before %. Example: 10%");

            BigDecimal pct;
            try {
                pct = new BigDecimal(pctStr.replace(",", "."));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid percentage: \"" + pctStr + "\". Use number like 10 or 10.5");
            }
            if (pct.compareTo(BigDecimal.ZERO) < 0)
                throw new IllegalArgumentException(
                        "Number after % must be ≥ 0. For deduction use minus sign before, example: -10%");
            if (pct.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException("Percentage must be ≤ 100. Enter: " + pctStr + "%");

            BigDecimal base = (baseSalary != null) ? baseSalary : BigDecimal.ZERO;
            absValue = base.multiply(pct).divide(new BigDecimal("100"), 0, RoundingMode.FLOOR);

        } else {
            // ── Fixed amount ─────────────────────────────────────────────────
            // Allow dot/comma as thousand separators: "500.000" "500,000"
            // Rule: if both present, remove all; if only one, it's the thousand separator
            String cleaned = trimmed.replace(".", "").replace(",", "");
            try {
                absValue = new BigDecimal(cleaned).setScale(0, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid amount: \"" + input.strip() + "\"." +
                        "\n• Amount: 500000  or  +500000  or  -200000" +
                        "\n• Percentage: 10%  or  +10%  or  -5%");
            }
        }

        if (absValue.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Absolute value must be ≥ 0.");

        BigDecimal result = negative ? absValue.negate() : absValue;

        if (!allowNegative && result.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Allowance value cannot be negative.");

        return result;
    }

    /** Overload — for Allowance (no negative allowed). */
    public BigDecimal parseAmount(String input, BigDecimal baseSalary) {
        return parseAmount(input, baseSalary, false);
    }

    /**
     * Create description string to display realtime hint label for Bonus field.
     *
     * Example results:
     *   "-10%"  + base 8.000.000  →  "= -800.000 ₫  ⚠ Deduction"
     *   "+10%"  + base 8.000.000  →  "= +800.000 ₫  🎉 Reward"
     *   "-200000"                 →  "= -200.000 ₫  ⚠ Deduction"
     *   "+500000"                 →  "= +500.000 ₫  🎉 Reward"
     *   "500000"                  →  ""  (plain positive: no hint needed)
     */
    public String describeBonusAmount(String input, BigDecimal baseSalary) {
        if (input == null || input.isBlank()) return "";
        String stripped = input.strip();

        // Show hint only when: has explicit sign (+ / -) OR is percentage
        boolean hasSign   = stripped.startsWith("-") || stripped.startsWith("+");
        boolean isPercent = stripped.endsWith("%") ||
                            stripped.replace("+", "").replace("-", "").endsWith("%");
        if (!hasSign && !isPercent) return "";

        try {
            BigDecimal amount = parseAmount(stripped, baseSalary, true);
            if (amount.compareTo(BigDecimal.ZERO) == 0) return "= 0 ₫";

            boolean isReward = amount.compareTo(BigDecimal.ZERO) > 0;
            String sign   = isReward ? "+" : "-";
            String label  = isReward ? "🎉 Reward" : "⚠ Deduction";
            String fmtAmt = String.format("%,.0f ₫", amount.abs());
            return "= " + sign + fmtAmt + "   " + label;
        } catch (Exception e) {
            return "⛔ " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validate + parse + build Salary object.
     *
     * Signed bonus auto-splits:
     *   signed > 0  →  DB.Bonus = signed,   DB.Deduction = 0
     *   signed < 0  →  DB.Bonus = 0,        DB.Deduction = abs(signed)
     *   signed = 0  →  DB.Bonus = 0,        DB.Deduction = 0
     */
    private Salary buildSalary(int salaryId,
                                int employeeId,
                                LocalDate month,
                                BigDecimal baseSalary,
                                String allowanceInput,
                                String bonusInput,
                                String status,
                                LocalDate paidDate,
                                String note) {

        if (employeeId <= 0)
            throw new IllegalArgumentException("Employee ID must be > 0.");
        if (month == null)
            throw new IllegalArgumentException("Salary month is required.");
        if (baseSalary == null || baseSalary.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Base salary must be > 0.");
        if (status == null || status.isBlank())
            throw new IllegalArgumentException("Status is required.");
        if ("PAID".equals(status) && paidDate == null)
            throw new IllegalArgumentException("Payment date is required when Status = PAID.");

        // Allowance always ≥ 0
        BigDecimal allowance = parseAmount(allowanceInput, baseSalary, false);

        // Bonus can be negative
        BigDecimal bonusSigned = parseAmount(bonusInput, baseSalary, true);

        // Split into correct DB columns
        BigDecimal bonus, deduction;
        if (bonusSigned.compareTo(BigDecimal.ZERO) >= 0) {
            bonus     = bonusSigned;
            deduction = BigDecimal.ZERO;
        } else {
            bonus     = BigDecimal.ZERO;
            deduction = bonusSigned.negate();   // store as positive value in Deduction column
        }

        Salary s = new Salary();
        if (salaryId > 0) s.setSalaryId(salaryId);
        s.setEmployeeId(employeeId);
        s.setSalaryMonth(month.withDayOfMonth(1));
        s.setBaseSalary(baseSalary);
        s.setAllowance(allowance);
        s.setBonus(bonus);
        s.setDeduction(deduction);
        s.setStatus(status);
        s.setPaidDate(paidDate);
        s.setNote(note == null || note.isBlank() ? null : note.trim());
        return s;
    }
}