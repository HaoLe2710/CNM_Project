package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.user.entity.UserBlock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBlockRepository
        extends BaseRepository<UserBlock, Long> {

    List<UserBlock> findByBlockerId(UUID blockerId);

    List<UserBlock> findByBlockerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID blockerId);

    Optional<UserBlock> findByBlockerIdAndBlockedIdAndDeletedAtIsNull(UUID blockerId, UUID blockedId);

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    boolean existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(UUID blockerId, UUID blockedId);

}