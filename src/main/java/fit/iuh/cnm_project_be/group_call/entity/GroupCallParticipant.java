package fit.iuh.cnm_project_be.group_call.entity;

import fit.iuh.cnm_project_be.group_call.enums.ParticipantState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "group_call_participants",
    uniqueConstraints = @UniqueConstraint(columnNames = {"group_call_id", "user_id"})
)
@Getter @Setter
public class GroupCallParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_call_id", nullable = false)
    private GroupCall groupCall;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ParticipantState state;

    @Column(name = "is_camera_on")
    private boolean isCameraOn = false;

    @Column(name = "is_mic_on")
    private boolean isMicOn = false;

    @Column(name = "join_count")
    private int joinCount = 0;

    @Column(name = "last_joined_at")
    private Instant lastJoinedAt;

    // Tổng thời gian online (tính bằng giây) - cộng dồn mỗi lần rời phòng
    @Column(name = "total_duration")
    private long totalDuration = 0L;
}
