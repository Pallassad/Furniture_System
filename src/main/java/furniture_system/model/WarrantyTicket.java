package furniture_system.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WarrantyTicket {
    private int ticketId;
    private int orderId;
    private int productId;
    private int customerId;
    private Integer handlerEmployeeId;   // nullable
    private String issueDesc;
    private String status;
    private BigDecimal cost;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Display-only (joined)
    private String customerName;
    private String productName;
    private String handlerName;

    public WarrantyTicket() {}

    // ── Getters & Setters ──────────────────────────────────
    public int getTicketId()                      { return ticketId; }
    public void setTicketId(int v)                { this.ticketId = v; }

    public int getOrderId()                       { return orderId; }
    public void setOrderId(int v)                 { this.orderId = v; }

    public int getProductId()                     { return productId; }
    public void setProductId(int v)               { this.productId = v; }

    public int getCustomerId()                    { return customerId; }
    public void setCustomerId(int v)              { this.customerId = v; }

    public Integer getHandlerEmployeeId()         { return handlerEmployeeId; }
    public void setHandlerEmployeeId(Integer v)   { this.handlerEmployeeId = v; }

    public String getIssueDesc()                  { return issueDesc; }
    public void setIssueDesc(String v)            { this.issueDesc = v; }

    public String getStatus()                     { return status; }
    public void setStatus(String v)               { this.status = v; }

    public BigDecimal getCost()                   { return cost; }
    public void setCost(BigDecimal v)             { this.cost = v; }

    public String getNote()                       { return note; }
    public void setNote(String v)                 { this.note = v; }

    public LocalDateTime getCreatedAt()           { return createdAt; }
    public void setCreatedAt(LocalDateTime v)     { this.createdAt = v; }

    public LocalDateTime getUpdatedAt()           { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)     { this.updatedAt = v; }

    public String getCustomerName()               { return customerName; }
    public void setCustomerName(String v)         { this.customerName = v; }

    public String getProductName()                { return productName; }
    public void setProductName(String v)          { this.productName = v; }

    public String getHandlerName()                { return handlerName; }
    public void setHandlerName(String v)          { this.handlerName = v; }

    @Override
    public String toString() {
        return "#" + ticketId + " – " + (productName != null ? productName : "Product " + productId);
    }
}
