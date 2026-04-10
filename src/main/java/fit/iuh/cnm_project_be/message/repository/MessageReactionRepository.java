package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.message.entity.MessageReaction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageReactionRepository extends BaseRepository<MessageReaction, Long> {

    Optional<MessageReaction> findByMessageIdAndUserId(Long messageId, UUID userId);

    List<MessageReaction> findByMessageIdIn(Collection<Long> messageIds);

    void deleteByMessageIdAndUserId(Long messageId, UUID userId);
}
