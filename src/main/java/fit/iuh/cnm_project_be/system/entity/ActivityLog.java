package fit.iuh.cnm_project_be.system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_logs")
@Getter @Setter
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID userId;
    private String action;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    private Instant createdAt = Instant.now();
}
