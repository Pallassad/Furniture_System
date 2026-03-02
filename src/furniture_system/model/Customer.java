package furniture_system.model;

import java.time.LocalDateTime;

public class Customer {

    public enum Gender { MALE, FEMALE, OTHER }
    public enum Status { ACTIVE, INACTIVE }

    private int           customerId;
    private String        fullName;
    private String        phone;
    private String        email;
    private Gender        gender;
    private Status        status;
    private LocalDateTime createdAt;

    public Customer() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public int           getCustomerId()              { return customerId; }
    public void          setCustomerId(int v)         { this.customerId = v; }

    public String        getFullName()                { return fullName; }
    public void          setFullName(String v)        { this.fullName = v; }

    public String        getPhone()                   { return phone; }
    public void          setPhone(String v)           { this.phone = v; }

    public String        getEmail()                   { return email; }
    public void          setEmail(String v)           { this.email = v; }

    public Gender        getGender()                  { return gender; }
    public void          setGender(Gender v)          { this.gender = v; }

    public Status        getStatus()                  { return status; }
    public void          setStatus(Status v)          { this.status = v; }

    public LocalDateTime getCreatedAt()               { return createdAt; }
    public void          setCreatedAt(LocalDateTime v){ this.createdAt = v; }

    public boolean       isActive() { return status == Status.ACTIVE; }

    @Override
    public String toString() {
        return customerId + " – " + fullName + " (" + phone + ")";
    }
}
