package fit.iuh.cnm_project_be.message.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message.dto.MessageAttachmentPayload;
import fit.iuh.cnm_project_be.message.dto.MessageResponse;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageAttachment;
import fit.iuh.cnm_project_be.message.entity.MessageUserState;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageAttachmentRepository;
import fit.iuh.cnm_project_be.message.repository.MessageReactionRepository;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.notification.service.NotificationContentBuilder;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.notification.service.NotificationRecipientResolver;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationUserSettingRepository;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageServiceVoiceMetadataTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageAttachmentRepository messageAttachmentRepository;
    @Mock
    private MessageReactionRepository messageReactionRepository;
    @Mock
    private MessageStatusRepository messageStatusRepository;
    @Mock
    private MessageUserStateRepository messageUserStateRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private ConversationUserSettingRepository conversationUserSettingRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private S3MediaStorageService s3MediaStorageService;
    @Mock
    private NotificationDispatcher notificationDispatcher;
    @Mock
    private NotificationRecipientResolver notificationRecipientResolver;
    @Mock
    private NotificationContentBuilder notificationContentBuilder;

    @InjectMocks
    private MessageService messageService;

    @Test
    void sendAudioMessageStoresAndReturnsAudioMetadata() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        prepareCommonSendFlow(conversationId, senderId, 700L);

        List<Double> waveform = buildWaveform(16);
        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setMessageType(MessageType.AUDIO);
        request.setAttachments(List.of(audioAttachmentPayload(
                "https://cdn.example.com/chat/voice-1.webm",
                "chat/u1/voice-1.webm",
                "voice-1.webm",
                "audio/webm",
                30_000L,
                12_345L,
                waveform,
                "webm"
        )));

        when(messageAttachmentRepository.findByMessageIdIn(List.of(700L))).thenReturn(List.of(savedAttachment(
                700L,
                "https://cdn.example.com/chat/voice-1.webm",
                "chat/u1/voice-1.webm",
                "voice-1.webm",
                "audio/webm",
                30_000L,
                MessageType.AUDIO,
                12_345L,
                "[0.05,0.1,0.2,0.4,0.35,0.3,0.25,0.2,0.15,0.1,0.12,0.14,0.16,0.18,0.2,0.22]",
                "webm"
        )));

        MessageResponse response = messageService.sendMessage(senderId, request);

        ArgumentCaptor<MessageAttachment> attachmentCaptor = ArgumentCaptor.forClass(MessageAttachment.class);
        verify(messageAttachmentRepository).save(attachmentCaptor.capture());
        MessageAttachment persistedAttachment = attachmentCaptor.getValue();

        assertThat(persistedAttachment.getAttachmentType()).isEqualTo(MessageType.AUDIO);
        assertThat(persistedAttachment.getDurationMs()).isEqualTo(12_345L);
        assertThat(persistedAttachment.getAudioFormat()).isEqualTo("webm");
        assertThat(persistedAttachment.getWaveform()).startsWith("[").endsWith("]");

        assertThat(response.getType()).isEqualTo(MessageType.AUDIO);
        assertThat(response.getAttachments()).hasSize(1);
        assertThat(response.getAttachments().getFirst().getDurationMs()).isEqualTo(12_345L);
        assertThat(response.getAttachments().getFirst().getAudioFormat()).isEqualTo("webm");
        assertThat(response.getAttachments().getFirst().getWaveform()).hasSize(16);
    }

    @Test
    void sendAudioMessageRejectsDurationAboveFiveMinutes() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        prepareCommonMembershipOnly(conversationId, senderId);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setMessageType(MessageType.AUDIO);
        request.setAttachments(List.of(audioAttachmentPayload(
                "https://cdn.example.com/chat/voice-long.webm",
                "chat/u1/voice-long.webm",
                "voice-long.webm",
                "audio/webm",
                30_000L,
                300_001L,
                buildWaveform(16),
                "webm"
        )));

        assertThatThrownBy(() -> messageService.sendMessage(senderId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duration");
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void sendAudioMessageRejectsInvalidWaveformLength() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        prepareCommonMembershipOnly(conversationId, senderId);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setMessageType(MessageType.AUDIO);
        request.setAttachments(List.of(audioAttachmentPayload(
                "https://cdn.example.com/chat/voice-wave.webm",
                "chat/u1/voice-wave.webm",
                "voice-wave.webm",
                "audio/webm",
                30_000L,
                10_000L,
                buildWaveform(10),
                "webm"
        )));

        assertThatThrownBy(() -> messageService.sendMessage(senderId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Waveform");
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void sendMessageRejectsAudioMetadataForNonAudioAttachment() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        prepareCommonMembershipOnly(conversationId, senderId);

        MessageAttachmentPayload payload = new MessageAttachmentPayload();
        payload.setUrl("https://cdn.example.com/chat/doc.pdf");
        payload.setStorageKey("chat/u1/doc.pdf");
        payload.setFileName("doc.pdf");
        payload.setContentType("application/pdf");
        payload.setFileSize(4096L);
        payload.setType(MessageType.FILE);
        payload.setDurationMs(5_000L);
        payload.setWaveform(buildWaveform(16));
        payload.setAudioFormat("mp3");

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setAttachments(List.of(payload));

        assertThatThrownBy(() -> messageService.sendMessage(senderId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Audio metadata");
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void sendAudioMessageRejectsWhenNoAudioAttachmentPresent() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        prepareCommonMembershipOnly(conversationId, senderId);

        MessageAttachmentPayload payload = new MessageAttachmentPayload();
        payload.setUrl("https://cdn.example.com/chat/doc.pdf");
        payload.setStorageKey("chat/u1/doc.pdf");
        payload.setFileName("doc.pdf");
        payload.setContentType("application/pdf");
        payload.setFileSize(4096L);
        payload.setType(MessageType.FILE);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setMessageType(MessageType.AUDIO);
        request.setAttachments(List.of(payload));

        assertThatThrownBy(() -> messageService.sendMessage(senderId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Audio message must include at least one audio attachment");
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void sendImageAttachmentStillWorksWithoutAudioMetadata() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        prepareCommonSendFlow(conversationId, senderId, 701L);

        MessageAttachmentPayload payload = new MessageAttachmentPayload();
        payload.setUrl("https://cdn.example.com/chat/img.png");
        payload.setStorageKey("chat/u1/img.png");
        payload.setFileName("img.png");
        payload.setContentType("image/png");
        payload.setFileSize(2_048L);
        payload.setType(MessageType.IMAGE);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setAttachments(List.of(payload));

        when(messageAttachmentRepository.findByMessageIdIn(List.of(701L))).thenReturn(List.of(savedAttachment(
                701L,
                "https://cdn.example.com/chat/img.png",
                "chat/u1/img.png",
                "img.png",
                "image/png",
                2_048L,
                MessageType.IMAGE,
                null,
                null,
                null
        )));

        MessageResponse response = messageService.sendMessage(senderId, request);

        assertThat(response.getType()).isEqualTo(MessageType.IMAGE);
        assertThat(response.getAttachments()).hasSize(1);
        assertThat(response.getAttachments().getFirst().getDurationMs()).isNull();
        assertThat(response.getAttachments().getFirst().getWaveform()).isNull();
        assertThat(response.getAttachments().getFirst().getAudioFormat()).isNull();
    }

    private void prepareCommonSendFlow(UUID conversationId, UUID senderId, long messageId) {
        prepareCommonMembershipOnly(conversationId, senderId);

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(messageId);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(messageId);
            return message;
        });
        when(messageUserStateRepository.findByMessageIdAndUserId(messageId, senderId))
                .thenReturn(Optional.of(senderState));
    }

    private void prepareCommonMembershipOnly(UUID conversationId, UUID senderId) {
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.PRIVATE);
        conversation.setCreatorId(senderId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, senderId))
                .thenReturn(Optional.of(member(conversationId, senderId)));
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId)));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(eq(conversationId), any(UUID.class)))
                .thenReturn(Optional.empty());
        when(messageRepository.findVisibleMessages(eq(conversationId), any(UUID.class), any()))
                .thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(eq(conversationId), any(UUID.class)))
                .thenReturn(0L);
        when(userProfileRepository.findAllById(any(Iterable.class)))
                .thenReturn(List.of(activeUser(senderId, "sender.user")));
        when(userProfileRepository.findById(senderId))
                .thenReturn(Optional.of(activeUser(senderId, "sender.user")));
    }

    private MessageAttachmentPayload audioAttachmentPayload(
            String url,
            String storageKey,
            String fileName,
            String contentType,
            long fileSize,
            long durationMs,
            List<Double> waveform,
            String audioFormat) {
        MessageAttachmentPayload payload = new MessageAttachmentPayload();
        payload.setUrl(url);
        payload.setStorageKey(storageKey);
        payload.setFileName(fileName);
        payload.setContentType(contentType);
        payload.setFileSize(fileSize);
        payload.setType(MessageType.AUDIO);
        payload.setDurationMs(durationMs);
        payload.setWaveform(waveform);
        payload.setAudioFormat(audioFormat);
        return payload;
    }

    private MessageAttachment savedAttachment(
            long messageId,
            String url,
            String storageKey,
            String fileName,
            String contentType,
            long fileSize,
            MessageType type,
            Long durationMs,
            String waveform,
            String audioFormat) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setMessageId(messageId);
        attachment.setFileUrl(url);
        attachment.setStorageKey(storageKey);
        attachment.setOriginalFileName(fileName);
        attachment.setFileType(contentType);
        attachment.setFileSize(fileSize);
        attachment.setAttachmentType(type);
        attachment.setDurationMs(durationMs);
        attachment.setWaveform(waveform);
        attachment.setAudioFormat(audioFormat);
        return attachment;
    }

    private ConversationMember member(UUID conversationId, UUID userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(MemberRole.MEMBER);
        return member;
    }

    private UserProfile activeUser(UUID userId, String username) {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(userId);
        userProfile.setUsername(username);
        userProfile.setDisplayName(username);
        return userProfile;
    }

    private List<Double> buildWaveform(int samples) {
        return IntStream.range(0, samples)
                .mapToObj(index -> Math.min(1d, 0.05d * (index + 1)))
                .toList();
    }
}
