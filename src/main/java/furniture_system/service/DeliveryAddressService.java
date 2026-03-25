package furniture_system.service;

import furniture_system.dao.DeliveryAddressDAO;
import furniture_system.model.DeliveryAddress;

import java.sql.SQLException;
import java.util.List;

/**
 * DeliveryAddressService
 *
 * Centralises all business rules for DeliveryAddress so that
 * controllers stay thin and validation is never duplicated.
 *
 * Rules enforced here:
 *  1. Phone: 9-11 digits, numeric only.
 *  2. Required fields: customerId > 0, receiverName, phone, addressLine, ward, district, city.
 *  3. Only one IsDefault per customer (delegated to DAO transaction).
 *  4. Soft-delete guard: if address is used in any Order → INACTIVE only.
 *  5. Status must be ACTIVE | INACTIVE.
 */
public class DeliveryAddressService {

    private final DeliveryAddressDAO dao = new DeliveryAddressDAO();

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    /** All addresses — for Admin table view. */
    public List<DeliveryAddress> getAll() throws SQLException {
        return dao.findAll();
    }

    /** Active addresses for a customer — for Employee order picker. */
    public List<DeliveryAddress> getByCustomerId(int customerId) throws SQLException {
        return dao.findByCustomerId(customerId);
    }

    /** Single address by PK. */
    public DeliveryAddress getById(int addressId) throws SQLException {
        return dao.findById(addressId);
    }

    /** Admin keyword search. */
    public List<DeliveryAddress> search(String keyword) throws SQLException {
        if (keyword == null || keyword.isBlank()) return getAll();
        return dao.search(keyword);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Add a new delivery address.
     *
     * @return generated AddressId
     * @throws IllegalArgumentException on validation failure
     * @throws SQLException             on DB error
     */
    public int addAddress(DeliveryAddress addr) throws SQLException {
        validate(addr);
        addr.setStatus("ACTIVE");
        return dao.insert(addr);
    }

    /**
     * Update an existing delivery address (Admin full update).
     *
     * @throws IllegalArgumentException if addressId invalid or validation fails
     */
    public void updateAddress(DeliveryAddress addr) throws SQLException {
        if (addr.getAddressId() <= 0)
            throw new IllegalArgumentException("Invalid AddressId.");
        validate(addr);
        validateStatus(addr.getStatus());
        if (!dao.update(addr))
            throw new SQLException("Update failed — record not found (AddressId=" + addr.getAddressId() + ").");
    }

    /**
     * Xoá địa chỉ:
     *  - Nếu có Order tham chiếu → soft-delete (Status = INACTIVE)
     *  - Nếu không có Order → hard-delete
     *
     * @throws SQLException on DB error
     */
    public void deleteAddress(int addressId) throws SQLException {
        DeliveryAddress existing = dao.findById(addressId);
        if (existing == null)
            throw new IllegalArgumentException("Không tìm thấy địa chỉ (AddressId=" + addressId + ").");

        if (dao.isLinkedToOrder(addressId)) {
            // Soft-delete: đang được dùng bởi Order
            if (!dao.softDelete(addressId))
                throw new SQLException("Soft-delete thất bại cho AddressId=" + addressId);
        } else {
            // Hard-delete
            if (!dao.hardDelete(addressId))
                throw new SQLException("Xoá địa chỉ thất bại (AddressId=" + addressId + ").");
        }
    }

    /**
     * Set a specific address as the default for a customer.
     * Unsets all other defaults atomically.
     */
    public void setDefault(int addressId, int customerId) throws SQLException {
        if (addressId <= 0 || customerId <= 0)
            throw new IllegalArgumentException("Invalid addressId or customerId.");
        if (!dao.setDefault(addressId, customerId))
            throw new SQLException("SetDefault failed for AddressId=" + addressId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATISTICS
    // ─────────────────────────────────────────────────────────────────────────

    /** Orders per city — for Admin report. */
    public List<Object[]> getOrdersByCity() throws SQLException {
        return dao.countOrdersByCity();
    }

    /** Orders per district — for Admin report. */
    public List<Object[]> getOrdersByDistrict() throws SQLException {
        return dao.countOrdersByDistrict();
    }

    /** Default vs non-default address usage rate. */
    public List<Object[]> getDefaultUsageRate() throws SQLException {
        return dao.defaultAddressUsageRate();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VALIDATION HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void validate(DeliveryAddress addr) {
        if (addr.getCustomerId() <= 0)
            throw new IllegalArgumentException("Customer is required.");
        if (isBlank(addr.getReceiverName()))
            throw new IllegalArgumentException("Receiver name is required.");
        if (addr.getReceiverName().length() > 100)
            throw new IllegalArgumentException("Receiver name must not exceed 100 characters.");
        validatePhone(addr.getPhone());
        if (isBlank(addr.getAddressLine()))
            throw new IllegalArgumentException("Address line is required.");
        if (isBlank(addr.getWard()))
            throw new IllegalArgumentException("Ward is required.");
        if (isBlank(addr.getDistrict()))
            throw new IllegalArgumentException("District is required.");
        if (isBlank(addr.getCity()))
            throw new IllegalArgumentException("City is required.");
    }

    /**
     * Phone: 9–11 digits, numeric only.
     * Matches DB CHECK constraint: NOT Phone LIKE '%[^0-9]%' AND LEN BETWEEN 9 AND 11.
     */
    public static void validatePhone(String phone) {
        if (isBlank(phone))
            throw new IllegalArgumentException("Phone is required.");
        String trimmed = phone.trim();
        if (!trimmed.matches("\\d{9,11}"))
            throw new IllegalArgumentException(
                    "Phone must contain 9–11 numeric digits only. Got: \"" + trimmed + "\"");
    }

    private void validateStatus(String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status))
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE.");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}