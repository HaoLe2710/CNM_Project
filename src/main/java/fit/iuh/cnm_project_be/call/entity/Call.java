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
    @GeneratedValue
    private UUID id;

    private UUID callerId;
    private UUID calleeId;

    private String channel;

    @Enumerated(EnumType.STRING)
    private CallStatus status;

    @Enumerated(EnumType.STRING)
    private CallType type;

    private Instant startedAt = Instant.now();
    private Instant endedAt;
    private Instant createdAt = Instant.now();
}
