package fit.iuh.cnm_project_be.user.entity;

import fit.iuh.cnm_project_be.user.enums.FriendRequestStatus;
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

    private UUID senderId;
    private UUID receiverId;

    @Enumerated(EnumType.STRING)
    private FriendRequestStatus status;

    private Instant createdAt = Instant.now();
    private Instant updatedAt;
}
