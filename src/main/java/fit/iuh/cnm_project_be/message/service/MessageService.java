package fit.iuh.cnm_project_be.message.service;

import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.dto.MessageDto;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    /**
     * Send message
     */
    @Transactional
    public MessageDto sendMessage(UUID senderId, SendMessageRequest request) {

        if (!conversationRepository.existsById(request.getConversationId())) {
            throw new NotFoundException("Conversation not found");
        }

        Message message = new Message();

        message.setConversationId(request.getConversationId());
        message.setSenderId(senderId);
        message.setContent(request.getContent());

        Message saved = messageRepository.save(message);

        return mapToDto(saved);
    }

    /**
     * Get conversation messages
     */
    @Transactional(readOnly = true)
    public List<MessageDto> getMessages(UUID conversationId) {

        List<Message> messages =
                messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(conversationId);

        return messages.stream()
                .map(this::mapToDto)
                .toList();
    }

    /**
     * Delete message (soft delete)
     */
    @Transactional
    public void deleteMessage(Long messageId, UUID userId) {

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("You cannot delete this message");
        }

        messageRepository.delete(message);
    }

    /**
     * Mapper
     */
    private MessageDto mapToDto(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}