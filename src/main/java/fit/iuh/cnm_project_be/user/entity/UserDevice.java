package fit.iuh.cnm_project_be.user.entity;

import jakarta.persistence.*;
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

    @Column(unique = true)
    private UUID userId;

    private String deviceId;
    private String pushToken;

    @Enumerated(EnumType.STRING)
    private Platform platform;

    private String deviceName;

    private Instant lastSeenAt = Instant.now();
    private Instant createdAt = Instant.now();
}
