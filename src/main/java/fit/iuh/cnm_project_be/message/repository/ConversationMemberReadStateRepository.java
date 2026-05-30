package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.message.entity.ConversationMemberReadState;
import fit.iuh.cnm_project_be.message.entity.ConversationMemberReadStateId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMemberReadStateRepository
        extends BaseRepository<ConversationMemberReadState, ConversationMemberReadStateId> {

    Optional<ConversationMemberReadState> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    List<ConversationMemberReadState> findByConversationIdOrderByLastReadMessageIdDescUpdatedAtDesc(UUID conversationId);

    @Query("""
            select state from ConversationMemberReadState state
            where state.conversationId = :conversationId
              and (state.lastReadMessageId is not null or state.lastDeliveredMessageId is not null)
            order by coalesce(state.lastReadMessageId, state.lastDeliveredMessageId) desc, state.updatedAt desc
            """)
    List<ConversationMemberReadState> findActiveByConversationId(@Param("conversationId") UUID conversationId);
}
