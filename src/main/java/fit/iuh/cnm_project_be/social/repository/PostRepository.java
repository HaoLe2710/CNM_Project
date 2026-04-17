package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.social.entity.Post;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PostRepository
        extends SoftDeleteRepository<Post, UUID> {

    List<Post> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    List<Post> findByDeletedAtIsNullAndArchivedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    List<Post> findByUserIdAndDeletedAtIsNullAndArchivedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Post> findByUserIdAndDeletedAtIsNullAndArchivedAtIsNotNullOrderByArchivedAtDesc(UUID userId, Pageable pageable);
}
