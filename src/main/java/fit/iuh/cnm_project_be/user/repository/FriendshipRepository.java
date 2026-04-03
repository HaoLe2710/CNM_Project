package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.user.entity.Friendship;

import java.util.List;
import java.util.UUID;

public interface FriendshipRepository
        extends SoftDeleteRepository<Friendship, UUID> {

    List<Friendship> findByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByUserIdAndFriendIdAndDeletedAtIsNull(UUID userId, UUID friendId);

    List<Friendship> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

}