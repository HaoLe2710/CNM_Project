package fit.iuh.cnm_project_be.message.enums;

/**
 * Delivery/transport semantics only.
 * User-view read state belongs to message_user_states.
 */
public enum MessageDeliveryStatus {
    SENT,
    DELIVERED,
    // Deprecated compatibility value accepted by updateStatus for older clients.
    @Deprecated
    SEEN
}
