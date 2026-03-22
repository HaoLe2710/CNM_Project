package fit.iuh.cnm_project_be.user.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE user_profiles SET deleted_at = now() WHERE user_id = ?")
public class UserProfile extends BaseEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    private String displayName;
    private String firstName;
    private String lastName;
    private String avatarUrl;

    @Column(unique = true)
    private String username;

    @Column(unique = true)
    private String inviteLink;

    private String qrCodeUrl;
    private String bio;
    private String phone;

    private Instant bannedUntil;

}