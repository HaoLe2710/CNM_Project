package fit.iuh.cnm_project_be.auth.entity;

import fit.iuh.cnm_project_be.auth.enums.Role;
import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.SQLDelete;

import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SQLDelete(sql = "UPDATE accounts SET deleted_at = now() WHERE user_id = ?")
@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id;

    @Column(unique = true)
    String username;

    @Column(unique = true, name = "user_id")
    private UUID userId;

    @NotBlank(message = "Password can not be empty")
    String password;

    @Enumerated(EnumType.STRING)
    List<Role> roles;
}
