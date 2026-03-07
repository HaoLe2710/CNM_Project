package fit.iuh.cnm_project_be.room.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.entity.ConversationMemberId;

import java.util.List;
import java.util.UUID;

public interface ConversationMemberRepository
        extends BaseRepository<ConversationMember, ConversationMemberId> {

    List<ConversationMember> findByUserId(UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);
}