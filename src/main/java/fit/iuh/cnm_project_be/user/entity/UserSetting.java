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
    private UUID userId;

    @Id
    private String key;

    @Column(columnDefinition = "jsonb")
    private String value;

    private Instant updatedAt = Instant.now();
}
