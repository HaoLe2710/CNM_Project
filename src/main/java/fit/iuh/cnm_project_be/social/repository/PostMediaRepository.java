package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.social.entity.PostMedia;

import java.util.List;
import java.util.UUID;

public interface PostMediaRepository extends BaseRepository<PostMedia, UUID> {

    List<PostMedia> findByPostIdOrderBySortOrderAscCreatedAtAsc(UUID postId);

    void deleteByPostId(UUID postId);
}
