package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.social.entity.PostLike;

import java.util.List;
import java.util.UUID;

public interface PostLikeRepository
        extends BaseRepository<PostLike, UUID> {

    List<PostLike> findByPostId(UUID postId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);
}