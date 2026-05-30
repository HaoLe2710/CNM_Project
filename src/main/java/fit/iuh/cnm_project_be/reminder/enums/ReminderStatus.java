package fit.iuh.cnm_project_be.reminder.enums;

import java.util.Optional;

public enum ReminderStatus {
    SCHEDULED,
    DUE,
    COMPLETED,
    CANCELLED;

    public static Optional<ReminderStatus> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (ReminderStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
