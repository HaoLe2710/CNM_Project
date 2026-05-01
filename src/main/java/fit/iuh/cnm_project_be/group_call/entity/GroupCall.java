package fit.iuh.cnm_project_be.group_call.entity;

import fit.iuh.cnm_project_be.group_call.enums.GroupCallStatus;
import fit.iuh.cnm_project_be.group_call.enums.GroupCallType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "group_calls")
@Getter @Setter
public class GroupCall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "initiator_id", nullable = false)
    private UUID initiatorId;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GroupCallStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private GroupCallType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    @OneToMany(mappedBy = "groupCall", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GroupCallParticipant> participants = new ArrayList<>();
}
