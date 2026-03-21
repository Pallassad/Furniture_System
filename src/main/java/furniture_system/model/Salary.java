package furniture_system.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Salary {
    private int salaryId;
    private int employeeId;
    private LocalDate salaryMonth;
    private BigDecimal baseSalary;
    private BigDecimal allowance;
    private BigDecimal bonus;
    private BigDecimal deduction;
    private BigDecimal finalSalary;   // computed by DB
    private String status;
    private LocalDate paidDate;       // nullable
    private String note;

    // Display-only (joined)
    private String employeeName;

    public Salary() {}

    // ── Getters & Setters ──────────────────────────────────
    public int getSalaryId()                    { return salaryId; }
    public void setSalaryId(int v)              { this.salaryId = v; }

    public int getEmployeeId()                  { return employeeId; }
    public void setEmployeeId(int v)            { this.employeeId = v; }

    public LocalDate getSalaryMonth()           { return salaryMonth; }
    public void setSalaryMonth(LocalDate v)     { this.salaryMonth = v; }

    public BigDecimal getBaseSalary()           { return baseSalary; }
    public void setBaseSalary(BigDecimal v)     { this.baseSalary = v; }

    public BigDecimal getAllowance()            { return allowance; }
    public void setAllowance(BigDecimal v)      { this.allowance = v; }

    public BigDecimal getBonus()               { return bonus; }
    public void setBonus(BigDecimal v)          { this.bonus = v; }

    public BigDecimal getDeduction()           { return deduction; }
    public void setDeduction(BigDecimal v)      { this.deduction = v; }

    public BigDecimal getFinalSalary()         { return finalSalary; }
    public void setFinalSalary(BigDecimal v)   { this.finalSalary = v; }

    public String getStatus()                  { return status; }
    public void setStatus(String v)            { this.status = v; }

    public LocalDate getPaidDate()             { return paidDate; }
    public void setPaidDate(LocalDate v)       { this.paidDate = v; }

    public String getNote()                    { return note; }
    public void setNote(String v)              { this.note = v; }

    public String getEmployeeName()            { return employeeName; }
    public void setEmployeeName(String v)      { this.employeeName = v; }

    /** Convenience: compute locally for previews (mirrors DB formula). */
    public BigDecimal computeFinalSalary() {
        BigDecimal base  = baseSalary  != null ? baseSalary  : BigDecimal.ZERO;
        BigDecimal allow = allowance   != null ? allowance   : BigDecimal.ZERO;
        BigDecimal bon   = bonus       != null ? bonus       : BigDecimal.ZERO;
        BigDecimal ded   = deduction   != null ? deduction   : BigDecimal.ZERO;
        return base.add(allow).add(bon).subtract(ded);
    }

    @Override
    public String toString() {
        return "Salary#" + salaryId + " [" + (employeeName != null ? employeeName : employeeId) + "]";
    }
}
