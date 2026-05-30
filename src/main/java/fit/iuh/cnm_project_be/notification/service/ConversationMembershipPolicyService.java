package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationMembershipPolicyService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;

    @Transactional(readOnly = true)
    public boolean conversationExists(UUID conversationId) {
        return findActiveConversation(conversationId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID conversationId, UUID userId) {
        if (conversationId == null || userId == null || !conversationExists(conversationId)) {
            return false;
        }
        return conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    @Transactional(readOnly = true)
    public boolean isPrivateConversation(UUID conversationId) {
        return findActiveConversation(conversationId)
                .map(conversation -> conversation.getType() == ConversationType.PRIVATE)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isGroupConversation(UUID conversationId) {
        return findActiveConversation(conversationId)
                .map(conversation -> conversation.getType() == ConversationType.GROUP)
                .orElse(false);
    }

    private Optional<Conversation> findActiveConversation(UUID conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        return conversationRepository.findById(conversationId)
                .filter(conversation -> !conversation.isDeleted());
    }
}
