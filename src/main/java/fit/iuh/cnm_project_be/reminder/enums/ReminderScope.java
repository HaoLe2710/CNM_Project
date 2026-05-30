package fit.iuh.cnm_project_be.reminder.enums;

import java.util.Optional;

public enum ReminderScope {
    TODAY,
    WEEK,
    UPCOMING,
    PAST;

    public static Optional<ReminderScope> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (ReminderScope scope : values()) {
            if (scope.name().equalsIgnoreCase(value.trim())) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }
}
