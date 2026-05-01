package fit.iuh.cnm_project_be.room.entity;

import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "conversation_user_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversation_user_settings_conversation_user", columnNames = {"conversation_id", "user_id"})
)
@Getter
@Setter
public class ConversationUserSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "muted_at")
    private Instant mutedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "pinned_at")
    private Instant pinnedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_level")
    private ConversationNotificationLevel notificationLevel;

    @Column(name = "custom_name")
    private String customName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
