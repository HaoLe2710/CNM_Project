package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.social.entity.Post;

import java.util.List;
import java.util.UUID;

public interface PostRepository
        extends SoftDeleteRepository<Post, UUID> {

    List<Post> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
}