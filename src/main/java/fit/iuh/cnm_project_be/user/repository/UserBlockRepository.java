package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.user.entity.UserBlock;

import java.util.List;
import java.util.UUID;

public interface UserBlockRepository
        extends BaseRepository<UserBlock, Long> {

    List<UserBlock> findByBlockerId(UUID blockerId);

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}