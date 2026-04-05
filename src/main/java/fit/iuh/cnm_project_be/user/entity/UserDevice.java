package fit.iuh.cnm_project_be.user.entity;

import fit.iuh.cnm_project_be.user.enums.Platform;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_devices")
@Getter @Setter
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column(unique = true, nullable = false)
    // unique khong lưu nhiều thiết bị cho một user
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String deviceId;

    private String pushToken;

    @Column(nullable = false)
    private Platform platform;

    private String deviceName;

    private Instant lastSeenAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
