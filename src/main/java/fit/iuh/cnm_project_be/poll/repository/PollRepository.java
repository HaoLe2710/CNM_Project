package fit.iuh.cnm_project_be.poll.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.poll.entity.Poll;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollRepository extends BaseRepository<Poll, UUID> {

    List<Poll> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID conversationId);

    Optional<Poll> findByIdAndDeletedAtIsNull(UUID id);
}
