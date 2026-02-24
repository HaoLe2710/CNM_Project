package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.message.entity.Message;

import java.util.List;
import java.util.UUID;

public interface MessageRepository
        extends SoftDeleteRepository<Message, Long> {

    List<Message> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID conversationId);

    List<Message> findTop50ByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID conversationId);
}