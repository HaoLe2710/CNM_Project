package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.social.entity.MomentView;

import java.util.UUID;

public interface MomentViewRepository extends BaseRepository<MomentView, Long> {
    boolean existsByMomentIdAndViewerId(UUID momentId, UUID viewerId);

    long countByMomentId(UUID momentId);
}
