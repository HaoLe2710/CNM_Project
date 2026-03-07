package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.social.entity.MomentTag;

import java.util.List;
import java.util.UUID;

public interface MomentTagRepository
        extends BaseRepository<MomentTag, Long> {

    List<MomentTag> findByMomentId(UUID momentId);

    List<MomentTag> findByTaggedUserId(UUID taggedUserId);
}