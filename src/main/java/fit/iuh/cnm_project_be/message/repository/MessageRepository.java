package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.message.entity.Message;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface MessageRepository
        extends SoftDeleteRepository<Message, Long> {

    List<Message> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    List<Message> findTop50ByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID conversationId);

    List<Message> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID conversationId);
}