package fit.iuh.cnm_project_be.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@IdClass(UserSettingId.class)
@Getter @Setter
public class UserSetting {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "key", nullable = false)
    private String key;

    @Column(columnDefinition = "jsonb")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
