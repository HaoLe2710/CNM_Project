package fit.iuh.cnm_project_be.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_blocks")
@Getter @Setter
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;
    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    private Instant deletedAt;
}
