package fit.iuh.cnm_project_be.message.service;

import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
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

    @Transactional
    public Message sendMessage(UUID conversationId, UUID senderId, String content) {

        if (!conversationRepository.existsById(conversationId)) {
            throw new NotFoundException("Conversation not found");
        }

        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setContent(content);
        message.setMessageType(MessageType.TEXT);

        return messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<Message> getMessages(UUID conversationId) {
        return messageRepository
                .findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(conversationId);
    }
}