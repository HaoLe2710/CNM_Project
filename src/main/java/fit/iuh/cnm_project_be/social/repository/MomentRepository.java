package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.social.entity.Moment;

import java.util.List;
import java.util.UUID;

public interface MomentRepository
        extends SoftDeleteRepository<Moment, UUID> {

    List<Moment> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
}