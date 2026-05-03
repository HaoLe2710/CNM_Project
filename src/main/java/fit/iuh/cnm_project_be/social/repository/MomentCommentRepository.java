package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.social.entity.MomentComment;

import java.util.List;
import java.util.UUID;

public interface MomentCommentRepository extends SoftDeleteRepository<MomentComment, UUID> {
    List<MomentComment> findByMomentIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID momentId);

    long countByMomentIdAndDeletedAtIsNull(UUID momentId);
}
