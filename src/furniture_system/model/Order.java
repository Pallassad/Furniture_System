package furniture_system.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Order {

    public enum Status {
        DRAFT, CONFIRMED, PAID, DELIVERING, COMPLETED, CANCELLED, RETURNED
    }

    private int orderId;
    private int customerId;
    private int employeeId;
    private int addressId;
    private Integer promoId;          // nullable
    private LocalDateTime orderDate;
    private String status;
    private BigDecimal subTotal;
    private BigDecimal discount;
    private BigDecimal finalTotal;
    private String note;

    // --- display helpers (JOIN columns) ---
    private String customerName;
    private String employeeName;

    public Order() {}

    public Order(int customerId, int employeeId, int addressId,
                 Integer promoId, String note) {
        this.customerId = customerId;
        this.employeeId = employeeId;
        this.addressId  = addressId;
        this.promoId    = promoId;
        this.note       = note;
        this.status     = Status.DRAFT.name();
        this.orderDate  = LocalDateTime.now();
        this.subTotal   = BigDecimal.ZERO;
        this.discount   = BigDecimal.ZERO;
        this.finalTotal = BigDecimal.ZERO;
    }

    // ── getters / setters ──────────────────────────────────────────────────

    public int getOrderId()                     { return orderId; }
    public void setOrderId(int orderId)         { this.orderId = orderId; }

    public int getCustomerId()                  { return customerId; }
    public void setCustomerId(int v)            { this.customerId = v; }

    public int getEmployeeId()                  { return employeeId; }
    public void setEmployeeId(int v)            { this.employeeId = v; }

    public int getAddressId()                   { return addressId; }
    public void setAddressId(int v)             { this.addressId = v; }

    public Integer getPromoId()                 { return promoId; }
    public void setPromoId(Integer v)           { this.promoId = v; }

    public LocalDateTime getOrderDate()         { return orderDate; }
    public void setOrderDate(LocalDateTime v)   { this.orderDate = v; }

    public String getStatus()                   { return status; }
    public void setStatus(String status)        { this.status = status; }

    public BigDecimal getSubTotal()             { return subTotal; }
    public void setSubTotal(BigDecimal v)       { this.subTotal = v; }

    public BigDecimal getDiscount()             { return discount; }
    public void setDiscount(BigDecimal v)       { this.discount = v; }

    public BigDecimal getFinalTotal()           { return finalTotal; }
    public void setFinalTotal(BigDecimal v)     { this.finalTotal = v; }

    public String getNote()                     { return note; }
    public void setNote(String note)            { this.note = note; }

    public String getCustomerName()             { return customerName; }
    public void setCustomerName(String v)       { this.customerName = v; }

    public String getEmployeeName()             { return employeeName; }
    public void setEmployeeName(String v)       { this.employeeName = v; }

    @Override
    public String toString() {
        return "Order#" + orderId + " [" + status + "] " + finalTotal;
    }
}
