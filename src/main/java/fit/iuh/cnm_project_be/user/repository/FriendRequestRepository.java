package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.user.entity.FriendRequest;
import fit.iuh.cnm_project_be.user.enums.FriendRequestStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendRequestRepository
        extends BaseRepository<FriendRequest, Long> {

    List<FriendRequest> findByReceiverId(UUID receiverId);

    boolean existsBySenderIdAndReceiverId(UUID senderId, UUID receiverId);

    List<FriendRequest> findByReceiverIdAndStatusOrderByCreatedAtDesc(UUID receiverId, FriendRequestStatus status);

    List<FriendRequest> findBySenderIdAndStatusOrderByCreatedAtDesc(UUID senderId, FriendRequestStatus status);

    boolean existsBySenderIdAndReceiverIdAndStatus(UUID senderId, UUID receiverId, FriendRequestStatus status);

    Optional<FriendRequest> findByIdAndReceiverId(Long id, UUID receiverId);
}