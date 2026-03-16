package fit.iuh.cnm_project_be.message.service;

import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.dto.MessageDto;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageStatus;
import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.room.dto.ConversationStatusPayload;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public MessageDto sendMessage(UUID senderId, SendMessageRequest request) {
        if (!conversationRepository.existsById(request.getConversationId())) {
            throw new NotFoundException("Conversation not found");
        }

        Message message = new Message();
        message.setConversationId(request.getConversationId());
        message.setSenderId(senderId);
        message.setContent(request.getContent());
        message.setMessageType(MessageType.TEXT);
        Message saved = messageRepository.save(message);

        MessageStatus status = new MessageStatus();
        status.setMessageId(saved.getId());
        status.setUserId(senderId);
        status.setStatus(MessageDeliveryStatus.SENT);
        messageStatusRepository.save(status);

        MessageDto response = mapToDto(saved);

        messagingTemplate.convertAndSend("/topic/conversations/" + request.getConversationId(), response);

        conversationMemberRepository.findByConversationId(request.getConversationId()).forEach(member -> {
            messagingTemplate.convertAndSend("/topic/users/" + member.getUserId() + "/conversations", response);
        });

        return response;
    }

    @Transactional(readOnly = true)
    public Slice<MessageDto> getMessages(UUID conversationId, int page) {
        Pageable pageable = PageRequest.of(page, 50, Sort.by("createdAt").descending());
        return messageRepository.findByConversationIdAndDeletedAtIsNull(conversationId, pageable)
                .map(this::mapToDto);
    }

    @Transactional
    public void deleteMessage(Long messageId, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("Permission denied");
        }

        message.setDeletedAt(ZonedDateTime.now().toInstant());
        messageRepository.save(message);
    }

    @Transactional
    public void updateStatus(Long messageId, UUID userId, MessageDeliveryStatus status) {
        if (!messageRepository.existsById(messageId)) throw new NotFoundException("Message not found");

        MessageStatus msgStatus = messageStatusRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> {
                    MessageStatus ns = new MessageStatus();
                    ns.setMessageId(messageId);
                    ns.setUserId(userId);
                    return ns;
                });

        msgStatus.setStatus(status);
        messageStatusRepository.save(msgStatus);

        messagingTemplate.convertAndSend("/topic/messages/" + messageId + "/status", status);
    }

    @Transactional
    public void markAsSeen(UUID conversationId, UUID userId) {
        messageStatusRepository.markAllAsSeen(conversationId, userId);

        ConversationStatusPayload payload = new ConversationStatusPayload(conversationId, "SEEN");

        messagingTemplate.convertAndSend("/topic/users/" + userId + "/conversations/status", payload);
    }

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