package fit.iuh.cnm_project_be.room.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.entity.ConversationMemberId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMemberRepository
        extends BaseRepository<ConversationMember, ConversationMemberId> {

    List<ConversationMember> findByConversationId(UUID conversationId);

    Optional<ConversationMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    long countByConversationId(UUID conversationId);

    void deleteByConversationIdAndUserId(UUID conversationId, UUID userId);

    @Query("SELECT cm.userId FROM ConversationMember cm WHERE cm.conversationId = :conversationId AND cm.userId <> :userId")
    Optional<UUID> findPartnerUserId(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);
}
