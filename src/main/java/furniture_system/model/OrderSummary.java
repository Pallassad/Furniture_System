package furniture_system.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OrderSummary – lightweight view object for Customer Purchase History.
 * Combines Order + Billing status + OrderLine items.
 */
public class OrderSummary {

    private int           orderId;
    private int           customerId;
    private LocalDateTime orderDate;
    private String        status;          // Order.Status
    private BigDecimal    subTotal;
    private BigDecimal    discount;
    private BigDecimal    finalTotal;
    private String        note;
    private String        billingStatus;   // Billing.BillingStatus (may be null)
    private String        paymentMethod;
    private List<OrderLineItem> lines;

    // ── Inner class for order lines ───────────────────────────────────────────
    public static class OrderLineItem {
        private int        lineId;
        private String     productName;
        private int        quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;

        public int        getLineId()       { return lineId; }
        public void       setLineId(int v)  { this.lineId = v; }
        public String     getProductName()       { return productName; }
        public void       setProductName(String v)   { this.productName = v; }
        public int        getQuantity()          { return quantity; }
        public void       setQuantity(int v)     { this.quantity = v; }
        public BigDecimal getUnitPrice()         { return unitPrice; }
        public void       setUnitPrice(BigDecimal v) { this.unitPrice = v; }
        public BigDecimal getLineTotal()         { return lineTotal; }
        public void       setLineTotal(BigDecimal v) { this.lineTotal = v; }
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public int           getOrderId()              { return orderId; }
    public void          setOrderId(int v)         { this.orderId = v; }
    public int           getCustomerId()           { return customerId; }
    public void          setCustomerId(int v)      { this.customerId = v; }
    public LocalDateTime getOrderDate()            { return orderDate; }
    public void          setOrderDate(LocalDateTime v) { this.orderDate = v; }
    public String        getStatus()               { return status; }
    public void          setStatus(String v)       { this.status = v; }
    public BigDecimal    getSubTotal()             { return subTotal; }
    public void          setSubTotal(BigDecimal v) { this.subTotal = v; }
    public BigDecimal    getDiscount()             { return discount; }
    public void          setDiscount(BigDecimal v) { this.discount = v; }
    public BigDecimal    getFinalTotal()           { return finalTotal; }
    public void          setFinalTotal(BigDecimal v){ this.finalTotal = v; }
    public String        getNote()                 { return note; }
    public void          setNote(String v)         { this.note = v; }
    public String        getBillingStatus()        { return billingStatus; }
    public void          setBillingStatus(String v){ this.billingStatus = v; }
    public String        getPaymentMethod()        { return paymentMethod; }
    public void          setPaymentMethod(String v){ this.paymentMethod = v; }
    public List<OrderLineItem> getLines()          { return lines; }
    public void          setLines(List<OrderLineItem> v) { this.lines = v; }
}
