package fit.iuh.cnm_project_be.social.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moment_views")
@Getter
@Setter
public class MomentView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "moment_id", nullable = false)
    private UUID momentId;

    @Column(name = "viewer_id", nullable = false)
    private UUID viewerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
