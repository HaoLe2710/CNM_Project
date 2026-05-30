package fit.iuh.cnm_project_be.room.enums;

import java.util.Arrays;
import java.util.Optional;

public enum GroupConversationLabel {
    FRIENDS("Bạn bè", "blue"),
    WORK("Công việc", "violet"),
    STUDY("Học tập", "amber"),
    FAMILY("Gia đình", "rose"),
    PROJECT("Dự án", "emerald"),
    OTHER("Khác", "slate");

    private final String displayName;
    private final String color;

    GroupConversationLabel(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public static Optional<GroupConversationLabel> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }

        String normalizedCode = code.trim();
        if (normalizedCode.isEmpty()) {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(label -> label.name().equalsIgnoreCase(normalizedCode))
                .findFirst();
    }
}
