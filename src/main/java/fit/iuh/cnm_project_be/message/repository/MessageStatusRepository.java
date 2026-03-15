package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.message.entity.MessageStatus;
import fit.iuh.cnm_project_be.message.entity.MessageStatusId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageStatusRepository
        extends BaseRepository<MessageStatus, MessageStatusId> {

    List<MessageStatus> findByUserId(UUID userId);

    Optional<MessageStatus> findByMessageIdAndUserId(Long messageId, UUID userId);
}