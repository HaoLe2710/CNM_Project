package fit.iuh.cnm_project_be.call.entity;

import fit.iuh.cnm_project_be.call.enums.CallStatus;
import fit.iuh.cnm_project_be.call.enums.CallType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calls")
@Getter @Setter
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caller_id", nullable = false)
    private UUID callerId;
    @Column(name = "callee_id", nullable = false)
    private UUID calleeId;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CallStatus status;

    @Enumerated(EnumType.STRING)
    private CallType type;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();
    private Instant endedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
