package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.message.entity.MessageStatus;
import fit.iuh.cnm_project_be.message.entity.MessageStatusId;

import java.util.Optional;
import java.util.UUID;

/**
 * Retained for delivery/transport semantics only.
 * SENT and DELIVERED are the primary states here; SEEN remains compatibility-only.
 * Visibility and seen state for user views live in MessageUserStateRepository.
 */
public interface MessageStatusRepository
        extends BaseRepository<MessageStatus, MessageStatusId> {

    Optional<MessageStatus> findByMessageIdAndUserId(Long messageId, UUID userId);
}
