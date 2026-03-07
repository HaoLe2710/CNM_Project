package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.user.entity.FriendRequest;

import java.util.List;
import java.util.UUID;

public interface FriendRequestRepository
        extends BaseRepository<FriendRequest, Long> {

    List<FriendRequest> findByReceiverId(UUID receiverId);

    boolean existsBySenderIdAndReceiverId(UUID senderId, UUID receiverId);
}