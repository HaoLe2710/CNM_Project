package fit.iuh.cnm_project_be.room.service;

import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID userId) {
        List<Conversation> conversations = conversationRepository.findAllByMemberId(userId);

        return conversations.stream().map(conv -> {
            List<Message> lastMsgs = messageRepository.findTop50ByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(conv.getId());
            Message lastMsg = lastMsgs.isEmpty() ? null : lastMsgs.get(0);

            return ConversationResponse.builder()
                    .id(conv.getId())
                    .name(conv.getName())
                    .type(String.valueOf(conv.getType()))
                    .lastMessage(lastMsg != null ? lastMsg.getContent() : "")
                    .lastMessageTime(lastMsg != null ? lastMsg.getCreatedAt() : conv.getCreatedAt())
                    .unreadCount(0L)
                    .build();
        }).toList();
    }
}