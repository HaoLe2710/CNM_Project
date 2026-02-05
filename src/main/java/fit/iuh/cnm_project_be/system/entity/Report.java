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

    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    private ReportTargetType targetType;

    private String targetId;
    private String reason;

    @Column(columnDefinition = "jsonb")
    private String aiAnalysis;

    private String actionTaken;

    private Instant createdAt = Instant.now();
    private Instant resolvedAt;
}
