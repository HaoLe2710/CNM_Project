package fit.iuh.cnm_project_be.system.entity;

import fit.iuh.cnm_project_be.system.enums.ReportTargetType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter @Setter
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;
    private String reason;

    @Column(columnDefinition = "jsonb")
    private String aiAnalysis;

    private String actionTaken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    private Instant resolvedAt;
}
