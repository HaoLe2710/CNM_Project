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
@SQLDelete(sql = "UPDATE accounts SET deleted_at = now() WHERE id = ?")
@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(unique = true, nullable = false)
    String username;

    private UUID userId;

    @NotBlank(message = "Password can not be empty")
    @Column(nullable = false)
    String password;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_roles", joinColumns = @JoinColumn(name = "account_id", referencedColumnName = "id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    List<Role> roles;
}
