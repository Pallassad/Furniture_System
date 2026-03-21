package furniture_system.model;

import java.time.LocalDateTime;

/**
 * DeliveryAddress model.
 * Maps 1-to-1 with the DeliveryAddress table.
 * customerName is a transient join field (not a DB column).
 */
public class DeliveryAddress {

    private int           addressId;
    private int           customerId;
    private String        customerName;   // transient — from JOIN with Customer
    private String        receiverName;
    private String        phone;
    private String        addressLine;
    private String        ward;
    private String        district;
    private String        city;
    private boolean       isDefault;
    private String        status;
    private LocalDateTime createdAt;

    public DeliveryAddress() {}

    // ── Getters / Setters ────────────────────────────────────────────────────

    public int getAddressId()                       { return addressId; }
    public void setAddressId(int v)                 { this.addressId = v; }

    public int getCustomerId()                      { return customerId; }
    public void setCustomerId(int v)                { this.customerId = v; }

    public String getCustomerName()                 { return customerName; }
    public void setCustomerName(String v)           { this.customerName = v; }

    public String getReceiverName()                 { return receiverName; }
    public void setReceiverName(String v)           { this.receiverName = v; }

    public String getPhone()                        { return phone; }
    public void setPhone(String v)                  { this.phone = v; }

    public String getAddressLine()                  { return addressLine; }
    public void setAddressLine(String v)            { this.addressLine = v; }

    public String getWard()                         { return ward; }
    public void setWard(String v)                   { this.ward = v; }

    public String getDistrict()                     { return district; }
    public void setDistrict(String v)               { this.district = v; }

    public String getCity()                         { return city; }
    public void setCity(String v)                   { this.city = v; }

    public boolean isDefault()                      { return isDefault; }
    public void setDefault(boolean v)               { this.isDefault = v; }

    public String getStatus()                       { return status; }
    public void setStatus(String v)                 { this.status = v; }

    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void setCreatedAt(LocalDateTime v)       { this.createdAt = v; }

    // ── Display helpers ──────────────────────────────────────────────────────

    /** One-line label for ComboBox / list cells. */
    public String getFullAddress() {
        return receiverName + " | " + addressLine + ", " + ward + ", " + district + ", " + city;
    }

    /** Label that also shows default badge — used in Employee order picker. */
    public String getDisplayLabel() {
        String base = getFullAddress();
        return isDefault ? "⭐ " + base : base;
    }

    @Override
    public String toString() { return getDisplayLabel(); }
}