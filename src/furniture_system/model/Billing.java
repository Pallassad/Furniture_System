package furniture_system.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Billing {

    public enum PaymentMethod { CASH, BANK_TRANSFER, CARD, OTHER }
    public enum BillingStatus  { UNPAID, PARTIAL, PAID, REFUNDED, VOID }

    private int invoiceId;
    private int orderId;
    private LocalDateTime issueDate;
    private String paymentMethod;
    private BigDecimal paidAmount;
    private String billingStatus;
    private String note;

    // display helpers
    private String customerName;
    private BigDecimal finalTotal;   // pulled from Order

    public Billing() {}

    public Billing(int orderId, String paymentMethod,
                   BigDecimal paidAmount, String billingStatus, String note) {
        this.orderId       = orderId;
        this.paymentMethod = paymentMethod;
        this.paidAmount    = paidAmount;
        this.billingStatus = billingStatus;
        this.note          = note;
        this.issueDate     = LocalDateTime.now();
    }

    // ── getters / setters ────────────────────────────────────────────────

    public int getInvoiceId()                       { return invoiceId; }
    public void setInvoiceId(int v)                 { this.invoiceId = v; }

    public int getOrderId()                         { return orderId; }
    public void setOrderId(int v)                   { this.orderId = v; }

    public LocalDateTime getIssueDate()             { return issueDate; }
    public void setIssueDate(LocalDateTime v)       { this.issueDate = v; }

    public String getPaymentMethod()                { return paymentMethod; }
    public void setPaymentMethod(String v)          { this.paymentMethod = v; }

    public BigDecimal getPaidAmount()               { return paidAmount; }
    public void setPaidAmount(BigDecimal v)         { this.paidAmount = v; }

    public String getBillingStatus()                { return billingStatus; }
    public void setBillingStatus(String v)          { this.billingStatus = v; }

    public String getNote()                         { return note; }
    public void setNote(String v)                   { this.note = v; }

    public String getCustomerName()                 { return customerName; }
    public void setCustomerName(String v)           { this.customerName = v; }

    public BigDecimal getFinalTotal()               { return finalTotal; }
    public void setFinalTotal(BigDecimal v)         { this.finalTotal = v; }
}
