package fit.iuh.cnm_project_be.room.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends SoftDeleteRepository<Conversation, UUID> {

    List<Conversation> findByCreatorIdAndDeletedAtIsNull(UUID creatorId);

    @Query("SELECT c FROM Conversation c JOIN ConversationMember cm ON c.id = cm.conversationId " +
            "WHERE cm.userId = :userId AND c.deletedAt IS NULL")
    List<Conversation> findAllByMemberId(@Param("userId") UUID userId);
}