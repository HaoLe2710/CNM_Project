package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.social.entity.MomentReaction;

import java.util.List;
import java.util.UUID;

public interface MomentReactionRepository
        extends BaseRepository<MomentReaction, Long> {

    List<MomentReaction> findByMomentId(UUID momentId);

    boolean existsByMomentIdAndUserId(UUID momentId, UUID userId);
}