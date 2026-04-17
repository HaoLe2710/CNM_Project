package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.social.entity.PostTag;

import java.util.List;
import java.util.UUID;

public interface PostTagRepository extends BaseRepository<PostTag, UUID> {

    List<PostTag> findByPostId(UUID postId);

    void deleteByPostId(UUID postId);
}
