package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.social.entity.PostVisibilityGrant;

import java.util.List;
import java.util.UUID;

public interface PostVisibilityGrantRepository extends BaseRepository<PostVisibilityGrant, UUID> {

    List<PostVisibilityGrant> findByPostId(UUID postId);

    boolean existsByPostIdAndViewerUserId(UUID postId, UUID viewerUserId);

    void deleteByPostId(UUID postId);
}
