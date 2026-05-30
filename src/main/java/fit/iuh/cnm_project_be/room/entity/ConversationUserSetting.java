package fit.iuh.cnm_project_be.room.entity;

import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import fit.iuh.cnm_project_be.room.enums.ConversationBackgroundType;
import fit.iuh.cnm_project_be.room.enums.GroupConversationLabel;
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

    @Column(name = "muted_until")
    private Instant mutedUntil;

    @Column(name = "last_muted_at")
    private Instant lastMutedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "pinned_at")
    private Instant pinnedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_level")
    private ConversationNotificationLevel notificationLevel;

    @Column(name = "custom_name")
    private String customName;

    @Enumerated(EnumType.STRING)
    @Column(name = "background_type")
    private ConversationBackgroundType backgroundType;

    @Column(name = "background_color")
    private String backgroundColor;

    @Column(name = "background_image_url")
    private String backgroundImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_label")
    private GroupConversationLabel groupLabel;

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
