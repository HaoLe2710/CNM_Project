package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.social.entity.PostComment;

import java.util.List;
import java.util.UUID;

public interface PostCommentRepository
        extends SoftDeleteRepository<PostComment, UUID> {

    List<PostComment> findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID postId);

    List<PostComment> findByPostIdAndParentCommentIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID postId, UUID parentCommentId);

    long countByPostIdAndDeletedAtIsNull(UUID postId);
}
