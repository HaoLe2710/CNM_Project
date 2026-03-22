package fit.iuh.cnm_project_be.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_account_id", columnList = "account_id")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefreshToken {
    @Id
    String token;

    @Column(nullable = false)
    LocalDateTime expiryDate;

    @ManyToOne()
    @JoinColumn(name = "account_id", referencedColumnName = "id")
    Account account;
}
