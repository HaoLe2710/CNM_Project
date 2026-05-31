package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.message.entity.MessageAttachment;
import fit.iuh.cnm_project_be.message.repository.MessageAttachmentRepository;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.user.dto.response.UserStorageCleanupResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserStorageFileItemResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserStorageSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserStorageService {

    private static final long LARGE_FILE_THRESHOLD_BYTES = 10L * 1024 * 1024;

    private final UserService userService;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    @Transactional(readOnly = true)
    public UserStorageSummaryResponse getSummary() {
        UUID userId = userService.getCurrentUserId();
        long totalBytes = defaultLong(messageAttachmentRepository.sumFileSizeBySenderId(userId));
        long largeFilesBytes = getLargeFiles(userId, 100).stream()
                .filter(item -> item.getSizeBytes() >= LARGE_FILE_THRESHOLD_BYTES)
                .mapToLong(UserStorageFileItemResponse::getSizeBytes)
                .sum();

        double chatDataMb = toMb(totalBytes);
        return UserStorageSummaryResponse.builder()
                .zaloDataMb(chatDataMb)
                .cacheMb(0)
                .largeFilesMb(toMb(largeFilesBytes))
                .chatDataMb(chatDataMb)
                .otherDataMb(0)
                .deviceTotalMb(0)
                .note("Server summary only covers app-managed synced attachments. Device-local cache and storage must be measured and cleaned by the mobile app.")
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserStorageFileItemResponse> getLargeFiles(int limit) {
        return getLargeFiles(userService.getCurrentUserId(), limit);
    }

    @Transactional(readOnly = true)
    public Page<UserStorageFileItemResponse> getSentMedia(String scope, int page, int size) {
        UUID userId = userService.getCurrentUserId();
        String normalizedScope = normalizeScope(scope);
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<MessageAttachment> attachmentPage = messageAttachmentRepository.findSentMediaBySenderIdAndScope(
                userId,
                normalizedScope,
                pageRequest);

        Map<Long, fit.iuh.cnm_project_be.message.entity.Message> messagesById = messageRepository.findAllById(
                        attachmentPage.getContent().stream().map(MessageAttachment::getMessageId).toList())
                .stream()
                .collect(Collectors.toMap(fit.iuh.cnm_project_be.message.entity.Message::getId, Function.identity()));

        Map<UUID, Conversation> conversationsById = conversationRepository.findAllById(
                        messagesById.values().stream().map(fit.iuh.cnm_project_be.message.entity.Message::getConversationId).toList())
                .stream()
                .collect(Collectors.toMap(Conversation::getId, Function.identity()));

        return attachmentPage.map(attachment -> {
            fit.iuh.cnm_project_be.message.entity.Message message = messagesById.get(attachment.getMessageId());
            Conversation conversation = message == null ? null : conversationsById.get(message.getConversationId());
            return toFileItem(attachment, message, conversation);
        });
    }

    public UserStorageCleanupResponse cleanupCache() {
        return cleanupResponse("cache");
    }

    public UserStorageCleanupResponse cleanupLargeFiles() {
        return cleanupResponse("large-files");
    }

    public UserStorageCleanupResponse cleanupChatData() {
        return cleanupResponse("chat-data");
    }

    private List<UserStorageFileItemResponse> getLargeFiles(UUID userId, int limit) {
        return messageAttachmentRepository.findLargestFilesBySenderId(userId, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(this::toFileItem)
                .toList();
    }

    private UserStorageFileItemResponse toFileItem(MessageAttachment attachment) {
        return toFileItem(attachment, null, null);
    }

    private UserStorageFileItemResponse toFileItem(
            MessageAttachment attachment,
            fit.iuh.cnm_project_be.message.entity.Message message,
            Conversation conversation) {
        long sizeBytes = defaultLong(attachment.getFileSize());
        return UserStorageFileItemResponse.builder()
                .id(attachment.getId())
                .messageId(attachment.getMessageId())
                .conversationId(message == null ? null : message.getConversationId())
                .conversationName(conversation == null ? null : conversation.getName())
                .fileUrl(attachment.getFileUrl())
                .thumbnailUrl(attachment.getFileUrl())
                .name(attachment.getOriginalFileName())
                .sizeBytes(sizeBytes)
                .sizeMb(toMb(sizeBytes))
                .type(attachment.getAttachmentType() == null ? "FILE" : attachment.getAttachmentType().name())
                .createdAt(attachment.getCreatedAt())
                .build();
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "ALL";
        }
        String normalizedScope = scope.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "GROUP", "PRIVATE").contains(normalizedScope)) {
            return "ALL";
        }
        return normalizedScope;
    }

    private UserStorageCleanupResponse cleanupResponse(String target) {
        return UserStorageCleanupResponse.builder()
                .target(target)
                .serverSideApplied(false)
                .action("CLIENT_SIDE_REQUIRED")
                .message("This screen represents device-local storage. Cleanup must be performed by the mobile app on the device.")
                .build();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private double toMb(long bytes) {
        return Math.round((bytes / 1024d / 1024d) * 100.0) / 100.0;
    }
}
