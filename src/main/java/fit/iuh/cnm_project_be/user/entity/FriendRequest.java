package fit.iuh.cnm_project_be.user.entity;

import fit.iuh.cnm_project_be.user.enums.FriendRequestStatus;
import fit.iuh.cnm_project_be.user.persistence.FriendRequestStatusConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "friend_requests")
@Getter
@Setter
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;
    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Column(name = "status", nullable = false)
//    @Enumerated(EnumType.STRING)
    @Convert(converter = FriendRequestStatusConverter.class)
    private FriendRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    private Instant updatedAt;
}
