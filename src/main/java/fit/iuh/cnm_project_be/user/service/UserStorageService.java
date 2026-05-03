package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.message.entity.MessageAttachment;
import fit.iuh.cnm_project_be.message.repository.MessageAttachmentRepository;
import fit.iuh.cnm_project_be.user.dto.response.UserStorageCleanupResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserStorageFileItemResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserStorageSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserStorageService {

    private static final long LARGE_FILE_THRESHOLD_BYTES = 10L * 1024 * 1024;

    private final UserService userService;
    private final MessageAttachmentRepository messageAttachmentRepository;

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
        long sizeBytes = defaultLong(attachment.getFileSize());
        return UserStorageFileItemResponse.builder()
                .id(attachment.getId())
                .thumbnailUrl(attachment.getFileUrl())
                .name(attachment.getOriginalFileName())
                .sizeBytes(sizeBytes)
                .sizeMb(toMb(sizeBytes))
                .type(attachment.getAttachmentType() == null ? "FILE" : attachment.getAttachmentType().name())
                .createdAt(attachment.getCreatedAt())
                .build();
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
