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
 *  CÁCH NHẬP BONUS (Thưởng / Phạt):
 *
 *  Số tiền cố định:
 *    500000  hoặc  +500000  →  Thưởng +500.000 ₫
 *    -200000                →  Phạt   -200.000 ₫
 *
 *  Theo % lương cơ bản:
 *    10%   hoặc  +10%       →  Thưởng 10% × BaseSalary
 *    -5%                    →  Phạt    5% × BaseSalary
 *
 *  Service tự tách khi lưu DB:
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
     * Thêm bản ghi lương mới.
     *
     * @param allowanceInput  Phụ cấp — số ≥ 0 hoặc "X%"
     * @param bonusInput      Thưởng/Phạt — có thể âm: "500000", "-200000", "10%", "-5%"
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
     * Cập nhật bản ghi lương.
     * Guard: PAID records không được sửa.
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
            throw new IllegalArgumentException("Không tìm thấy bản ghi #" + salaryId);
        if ("PAID".equals(existing.getStatus()))
            throw new IllegalArgumentException("Không thể sửa bản ghi đã PAID.");

        Salary s = buildSalary(salaryId, employeeId, month, baseSalary,
                allowanceInput, bonusInput, status, paidDate, note);
        dao.update(s);
    }

    /** Xoá — chỉ DRAFT. */
    public void deleteSalary(int salaryId) throws SQLException {
        dao.delete(salaryId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PARSING  (public — Controller dùng cho live preview)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parse chuỗi nhập thành số tiền có dấu (signed BigDecimal).
     *
     * Định dạng chấp nhận:
     *   "500000"   →  +500.000   (thưởng)
     *   "+500000"  →  +500.000   (thưởng tường minh)
     *   "-200000"  →  -200.000   (phạt)
     *   "10%"      →  +10% × base
     *   "+10%"     →  +10% × base
     *   "-5%"      →  - 5% × base  (phạt)
     *   ""  / null →  0
     *
     * @param input         Chuỗi người dùng nhập
     * @param baseSalary    Lương cơ bản (dùng khi tính %)
     * @param allowNegative true  = chấp nhận âm (Bonus/Phạt)
     *                      false = chỉ nhận ≥ 0 (Allowance)
     */
    public BigDecimal parseAmount(String input, BigDecimal baseSalary, boolean allowNegative) {
        if (input == null || input.isBlank()) return BigDecimal.ZERO;

        String trimmed = input.strip();

        // ── Tách dấu ở đầu ───────────────────────────────────────────────────
        boolean negative = false;
        if (trimmed.startsWith("-")) {
            negative = true;
            trimmed  = trimmed.substring(1).strip();
        } else if (trimmed.startsWith("+")) {
            trimmed  = trimmed.substring(1).strip();
        }

        if (trimmed.isEmpty())
            throw new IllegalArgumentException("Giá trị trống. Nhập số tiền hoặc phần trăm.");

        BigDecimal absValue;

        // ── Phần trăm ─────────────────────────────────────────────────────────
        if (trimmed.endsWith("%")) {
            String pctStr = trimmed.substring(0, trimmed.length() - 1).strip();
            if (pctStr.isEmpty())
                throw new IllegalArgumentException("Thiếu số trước dấu %. Ví dụ: 10%");

            BigDecimal pct;
            try {
                pct = new BigDecimal(pctStr.replace(",", "."));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Phần trăm không hợp lệ: \"" + pctStr + "\". Dùng số như 10 hoặc 10.5");
            }
            if (pct.compareTo(BigDecimal.ZERO) < 0)
                throw new IllegalArgumentException(
                        "Số sau % phải ≥ 0. Để phạt dùng dấu - ở trước, ví dụ: -10%");
            if (pct.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException("Phần trăm phải ≤ 100. Nhập: " + pctStr + "%");

            BigDecimal base = (baseSalary != null) ? baseSalary : BigDecimal.ZERO;
            absValue = base.multiply(pct).divide(new BigDecimal("100"), 0, RoundingMode.FLOOR);

        } else {
            // ── Số tiền cố định ───────────────────────────────────────────────
            // Cho phép dấu chấm/phẩy ngăn cách hàng nghìn: "500.000" "500,000"
            // Quy tắc: nếu có cả hai, loại bỏ tất cả; nếu chỉ có một thì đó là hàng nghìn
            String cleaned = trimmed.replace(".", "").replace(",", "");
            try {
                absValue = new BigDecimal(cleaned).setScale(0, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Số tiền không hợp lệ: \"" + input.strip() + "\"." +
                        "\n• Số tiền: 500000  hoặc  +500000  hoặc  -200000" +
                        "\n• Phần trăm: 10%  hoặc  +10%  hoặc  -5%");
            }
        }

        if (absValue.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Giá trị tuyệt đối phải ≥ 0.");

        BigDecimal result = negative ? absValue.negate() : absValue;

        if (!allowNegative && result.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Giá trị phụ cấp không được âm.");

        return result;
    }

    /** Overload — dùng cho Allowance (không cho phép âm). */
    public BigDecimal parseAmount(String input, BigDecimal baseSalary) {
        return parseAmount(input, baseSalary, false);
    }

    /**
     * Tạo chuỗi mô tả hiển thị hint label realtime cho field Bonus.
     *
     * Ví dụ kết quả:
     *   "-10%"  + base 8.000.000  →  "= -800.000 ₫  ⚠ Phạt"
     *   "+10%"  + base 8.000.000  →  "= +800.000 ₫  🎉 Thưởng"
     *   "-200000"                 →  "= -200.000 ₫  ⚠ Phạt"
     *   "+500000"                 →  "= +500.000 ₫  🎉 Thưởng"
     *   "500000"                  →  ""  (plain positive: không cần hint)
     */
    public String describeBonusAmount(String input, BigDecimal baseSalary) {
        if (input == null || input.isBlank()) return "";
        String stripped = input.strip();

        // Chỉ hiện hint khi: có dấu tường minh (+ / -) HOẶC là phần trăm
        boolean hasSign   = stripped.startsWith("-") || stripped.startsWith("+");
        boolean isPercent = stripped.endsWith("%") ||
                            stripped.replace("+", "").replace("-", "").endsWith("%");
        if (!hasSign && !isPercent) return "";

        try {
            BigDecimal amount = parseAmount(stripped, baseSalary, true);
            if (amount.compareTo(BigDecimal.ZERO) == 0) return "= 0 ₫";

            boolean isReward = amount.compareTo(BigDecimal.ZERO) > 0;
            String sign   = isReward ? "+" : "-";
            String label  = isReward ? "🎉 Thưởng" : "⚠ Phạt";
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
     * Bonus signed tự động tách:
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
            throw new IllegalArgumentException("Employee ID phải > 0.");
        if (month == null)
            throw new IllegalArgumentException("Tháng lương là bắt buộc.");
        if (baseSalary == null || baseSalary.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Lương cơ bản phải > 0.");
        if (status == null || status.isBlank())
            throw new IllegalArgumentException("Trạng thái là bắt buộc.");
        if ("PAID".equals(status) && paidDate == null)
            throw new IllegalArgumentException("Ngày thanh toán bắt buộc khi Status = PAID.");

        // Allowance luôn ≥ 0
        BigDecimal allowance = parseAmount(allowanceInput, baseSalary, false);

        // Bonus có thể âm
        BigDecimal bonusSigned = parseAmount(bonusInput, baseSalary, true);

        // Tách vào đúng cột DB
        BigDecimal bonus, deduction;
        if (bonusSigned.compareTo(BigDecimal.ZERO) >= 0) {
            bonus     = bonusSigned;
            deduction = BigDecimal.ZERO;
        } else {
            bonus     = BigDecimal.ZERO;
            deduction = bonusSigned.negate();   // lưu giá trị dương vào cột Deduction
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