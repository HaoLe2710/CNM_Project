package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.message.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends SoftDeleteRepository<Message, Long> {
    Slice<Message> findByConversationIdAndDeletedAtIsNull(UUID conversationId, Pageable pageable);

    List<Message> findTop50ByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID conversationId);

    List<Message> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID conversationId);
}