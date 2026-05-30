package fit.iuh.cnm_project_be.notification.entity;

import fit.iuh.cnm_project_be.notification.enums.DevicePlatform;
import fit.iuh.cnm_project_be.notification.enums.PushProvider;
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
        name = "device_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_device_tokens_user_device_platform",
                columnNames = {"user_id", "device_id", "platform"}
        )
)
@Getter
@Setter
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DevicePlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PushProvider provider;

    @Column(nullable = false, columnDefinition = "text")
    private String token;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
