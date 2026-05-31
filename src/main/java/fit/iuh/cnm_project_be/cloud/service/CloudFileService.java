package fit.iuh.cnm_project_be.cloud.service;

import fit.iuh.cnm_project_be.cloud.dto.request.CreateCloudFolderRequest;
import fit.iuh.cnm_project_be.cloud.dto.request.CreateCloudLinkRequest;
import fit.iuh.cnm_project_be.cloud.dto.request.CreateCloudManualItemRequest;
import fit.iuh.cnm_project_be.cloud.dto.request.RenameCloudFileRequest;
import fit.iuh.cnm_project_be.cloud.dto.request.SendCloudFileToConversationRequest;
import fit.iuh.cnm_project_be.cloud.dto.response.CloudFileAnalysisResponse;
import fit.iuh.cnm_project_be.cloud.dto.response.CloudFilePageResponse;
import fit.iuh.cnm_project_be.cloud.dto.response.CloudFileResponse;
import fit.iuh.cnm_project_be.cloud.dto.response.CloudStorageSummaryResponse;
import fit.iuh.cnm_project_be.cloud.entity.CloudFile;
import fit.iuh.cnm_project_be.cloud.enums.CloudFileType;
import fit.iuh.cnm_project_be.cloud.repository.CloudFileRepository;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.dto.MessageAttachmentPayload;
import fit.iuh.cnm_project_be.message.dto.MessageResponse;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.dto.UploadAttachmentResponse;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.service.MessageService;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudFileService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 255;
    private static final String CLOUD_PATH_PREFIX = "cloud";
    private static final long FALLBACK_DEFAULT_QUOTA_BYTES = 1_073_741_824L;

    private final CloudFileRepository cloudFileRepository;
    private final S3MediaStorageService s3MediaStorageService;
    private final MessageService messageService;
    private final UserService userService;

    @Value("${app.cloud.storage.default-quota-bytes:1073741824}")
    private long defaultQuotaBytes;

    @Transactional(readOnly = true)
    public CloudFilePageResponse listMyFiles(
            UUID parentFolderId,
            String query,
            String typeCode,
            Integer page,
            Integer size) {
        UUID currentUserId = userService.getCurrentUserId();
        validateParentFolder(currentUserId, parentFolderId);

        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        CloudFileType fileTypeFilter = normalizeFileType(typeCode);
        String normalizedQuery = normalizeSearchQuery(query);

        Page<CloudFile> filePage = cloudFileRepository.searchActiveFiles(
                currentUserId,
                parentFolderId,
                fileTypeFilter,
                normalizedQuery,
                PageRequest.of(normalizedPage, normalizedSize));

        return CloudFilePageResponse.builder()
                .items(filePage.getContent().stream().map(this::toResponse).toList())
                .page(filePage.getNumber())
                .size(filePage.getSize())
                .totalItems(filePage.getTotalElements())
                .totalPages(filePage.getTotalPages())
                .hasMore(filePage.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public CloudFilePageResponse listMyTrash(
            String query,
            String typeCode,
            Integer page,
            Integer size) {
        UUID currentUserId = userService.getCurrentUserId();

        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        CloudFileType fileTypeFilter = normalizeFileType(typeCode);
        String normalizedQuery = normalizeSearchQuery(query);

        Page<CloudFile> filePage = cloudFileRepository.searchDeletedFiles(
                currentUserId,
                fileTypeFilter,
                normalizedQuery,
                PageRequest.of(normalizedPage, normalizedSize));

        return CloudFilePageResponse.builder()
                .items(filePage.getContent().stream().map(this::toResponse).toList())
                .page(filePage.getNumber())
                .size(filePage.getSize())
                .totalItems(filePage.getTotalElements())
                .totalPages(filePage.getTotalPages())
                .hasMore(filePage.hasNext())
                .build();
    }

    @Transactional
    public CloudFileResponse uploadMyFile(MultipartFile file, UUID parentFolderId) {
        UUID currentUserId = userService.getCurrentUserId();
        validateParentFolder(currentUserId, parentFolderId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Cloud file is required");
        }
        ensureUploadWithinQuota(currentUserId, file.getSize());

        String originalFileName = sanitizeUploadedFileName(file.getOriginalFilename());
        String cloudPathPrefix = buildCloudPathPrefix(currentUserId);
        UploadAttachmentResponse uploadedFile = s3MediaStorageService.upload(currentUserId, file, cloudPathPrefix);
        CloudFileType fileType = CloudFileType.detect(
                false,
                uploadedFile.getContentType(),
                originalFileName);

        CloudFile cloudFile = new CloudFile();
        cloudFile.setOwnerId(currentUserId);
        cloudFile.setParentFolderId(parentFolderId);
        cloudFile.setName(originalFileName);
        cloudFile.setOriginalFileName(originalFileName);
        cloudFile.setMimeType(uploadedFile.getContentType());
        cloudFile.setFileExtension(CloudFileType.extractExtension(originalFileName));
        cloudFile.setFileSize(uploadedFile.getFileSize());
        cloudFile.setFileType(fileType);
        cloudFile.setFileUrl(uploadedFile.getUrl());
        cloudFile.setStorageKey(uploadedFile.getStorageKey());
        cloudFile.setFolder(false);
        cloudFile.setDurationMs(null);
        cloudFile.setWaveform(null);
        cloudFile.setAudioFormat(null);

        return toResponse(cloudFileRepository.save(cloudFile));
    }

    @Transactional
    public CloudFileResponse createFolder(CreateCloudFolderRequest request) {
        UUID currentUserId = userService.getCurrentUserId();
        UUID parentFolderId = request.getParentFolderId();
        validateParentFolder(currentUserId, parentFolderId);

        CloudFile folder = new CloudFile();
        folder.setOwnerId(currentUserId);
        folder.setParentFolderId(parentFolderId);
        folder.setName(normalizeName(request.getName()));
        folder.setOriginalFileName(null);
        folder.setMimeType(null);
        folder.setFileExtension(null);
        folder.setFileSize(null);
        folder.setFileType(CloudFileType.FOLDER);
        folder.setFileUrl(null);
        folder.setStorageKey(null);
        folder.setFolder(true);
        folder.setDurationMs(null);
        folder.setWaveform(null);
        folder.setAudioFormat(null);

        return toResponse(cloudFileRepository.save(folder));
    }

    @Transactional
    public CloudFileResponse createLink(CreateCloudLinkRequest request) {
        UUID currentUserId = userService.getCurrentUserId();
        UUID parentFolderId = request.getParentFolderId();
        validateParentFolder(currentUserId, parentFolderId);

        CloudFile link = new CloudFile();
        link.setOwnerId(currentUserId);
        link.setParentFolderId(parentFolderId);
        link.setName(normalizeName(request.getName()));
        link.setOriginalFileName(null);
        link.setMimeType("text/uri-list");
        link.setFileExtension(null);
        link.setFileSize(0L);
        link.setFileType(CloudFileType.LINK);
        link.setFileUrl(normalizeUrl(request.getUrl()));
        link.setStorageKey(null);
        link.setFolder(false);
        link.setDurationMs(null);
        link.setWaveform(null);
        link.setAudioFormat(null);
        link.setManualContent(null);

        return toResponse(cloudFileRepository.save(link));
    }

    @Transactional
    public CloudFileResponse createManualItem(CreateCloudManualItemRequest request) {
        UUID currentUserId = userService.getCurrentUserId();
        UUID parentFolderId = request.getParentFolderId();
        validateParentFolder(currentUserId, parentFolderId);

        String content = normalizeNullableText(request.getContent());
        CloudFile manualItem = new CloudFile();
        manualItem.setOwnerId(currentUserId);
        manualItem.setParentFolderId(parentFolderId);
        manualItem.setName(normalizeName(request.getName()));
        manualItem.setOriginalFileName(null);
        manualItem.setMimeType("text/plain");
        manualItem.setFileExtension("txt");
        manualItem.setFileSize(content != null ? (long) content.length() : 0L);
        manualItem.setFileType(CloudFileType.MANUAL);
        manualItem.setFileUrl(null);
        manualItem.setStorageKey(null);
        manualItem.setFolder(false);
        manualItem.setDurationMs(null);
        manualItem.setWaveform(null);
        manualItem.setAudioFormat(null);
        manualItem.setManualContent(content);

        return toResponse(cloudFileRepository.save(manualItem));
    }

    @Transactional
    public CloudFileResponse rename(UUID fileId, RenameCloudFileRequest request) {
        UUID currentUserId = userService.getCurrentUserId();
        CloudFile file = getOwnedActiveFile(currentUserId, fileId);
        file.setName(normalizeName(request.getName()));
        return toResponse(cloudFileRepository.save(file));
    }

    @Transactional
    public void delete(UUID fileId) {
        UUID currentUserId = userService.getCurrentUserId();
        CloudFile file = getOwnedActiveFile(currentUserId, fileId);
        if (file.isFolder() && cloudFileRepository.existsActiveChildren(currentUserId, file.getId())) {
            throw new BusinessException("Cannot delete non-empty folder");
        }

        file.setDeletedAt(Instant.now());
        cloudFileRepository.save(file);
    }

    @Transactional
    public CloudFileResponse restore(UUID fileId) {
        UUID currentUserId = userService.getCurrentUserId();
        CloudFile file = getOwnedFile(currentUserId, fileId);
        if (file.getDeletedAt() == null) {
            return toResponse(file);
        }

        if (file.getParentFolderId() != null) {
            Optional<CloudFile> parentFolder = cloudFileRepository.findByIdAndOwnerId(file.getParentFolderId(), currentUserId);
            if (parentFolder.isEmpty() || parentFolder.get().getDeletedAt() != null || !parentFolder.get().isFolder()) {
                file.setParentFolderId(null);
            }
        }

        file.setDeletedAt(null);
        return toResponse(cloudFileRepository.save(file));
    }

    @Transactional
    public void permanentlyDelete(UUID fileId) {
        UUID currentUserId = userService.getCurrentUserId();
        CloudFile file = getOwnedFile(currentUserId, fileId);
        if (file.getDeletedAt() == null) {
            throw new BusinessException("Only files in trash can be permanently deleted");
        }

        if (file.isFolder() && cloudFileRepository.existsAnyChildren(currentUserId, file.getId())) {
            throw new BusinessException("Cannot permanently delete non-empty folder");
        }

        if (!file.isFolder()) {
            deleteFileFromStorage(file);
        }

        cloudFileRepository.delete(file);
    }

    @Transactional
    public MessageResponse sendFileToConversation(UUID fileId, SendCloudFileToConversationRequest request) {
        UUID currentUserId = userService.getCurrentUserId();
        CloudFile cloudFile = getOwnedActiveFile(currentUserId, fileId);
        if (cloudFile.isFolder()) {
            throw new BusinessException("Folders cannot be sent to conversation");
        }

        MessageType messageType = mapCloudFileTypeToMessageType(cloudFile.getFileType());
        MessageAttachmentPayload attachmentPayload = toMessageAttachmentPayload(cloudFile, messageType);

        SendMessageRequest sendMessageRequest = new SendMessageRequest();
        sendMessageRequest.setConversationId(request.getConversationId());
        sendMessageRequest.setContent(normalizeNullableText(request.getMessage()));
        sendMessageRequest.setMessageType(messageType);
        sendMessageRequest.setAttachments(List.of(attachmentPayload));

        return messageService.sendMessage(currentUserId, sendMessageRequest);
    }

    @Transactional(readOnly = true)
    public CloudFileAnalysisResponse getFileAnalysis(UUID fileId) {
        UUID currentUserId = userService.getCurrentUserId();
        CloudFile cloudFile = getOwnedActiveFile(currentUserId, fileId);
        ensureImageAnalysisTarget(cloudFile);
        return buildAnalysisResponse(cloudFile);
    }

    @Transactional
    public CloudFileAnalysisResponse runFileAnalysis(UUID fileId) {
        UUID currentUserId = userService.getCurrentUserId();
        CloudFile cloudFile = getOwnedActiveFile(currentUserId, fileId);
        ensureImageAnalysisTarget(cloudFile);
        cloudFile.setAnalysisStatus("Trạng thái phân tích: Phát hiện bệnh");
        cloudFile.setDetectedDisease("rust");
        cloudFile.setSeverityLevel("Cao");
        cloudFile.setAnalysisConfidence(0.92D);
        cloudFile.setAnalyzedAt(Instant.now());
        return buildAnalysisResponse(cloudFileRepository.save(cloudFile));
    }

    @Transactional(readOnly = true)
    public CloudStorageSummaryResponse getSummary() {
        UUID currentUserId = userService.getCurrentUserId();

        Map<String, Long> byType = new LinkedHashMap<>();
        byType.put(CloudFileType.IMAGE.name(), 0L);
        byType.put(CloudFileType.VIDEO.name(), 0L);
        byType.put(CloudFileType.AUDIO.name(), 0L);
        byType.put(CloudFileType.DOCUMENT.name(), 0L);
        byType.put(CloudFileType.ARCHIVE.name(), 0L);
        byType.put(CloudFileType.LINK.name(), 0L);
        byType.put(CloudFileType.MANUAL.name(), 0L);
        byType.put(CloudFileType.OTHER.name(), 0L);

        cloudFileRepository.summarizeStorageByType(currentUserId).forEach(summary -> {
            CloudFileType fileType = summary.getFileType();
            if (fileType == null || fileType == CloudFileType.FOLDER) {
                return;
            }
            byType.put(fileType.name(), defaultLong(summary.getTotalBytes()));
        });

        long activeBytes = defaultLong(cloudFileRepository.sumActiveFileSizeByOwner(currentUserId));
        long trashBytes = defaultLong(cloudFileRepository.sumTrashFileSizeByOwner(currentUserId));
        long usedBytes = defaultLong(cloudFileRepository.sumUsedFileSizeByOwner(currentUserId));
        long quotaBytes = getQuotaBytes(currentUserId);
        long remainingBytes = Math.max(quotaBytes - usedBytes, 0L);
        double usagePercent = quotaBytes <= 0 ? 0D : (double) usedBytes * 100D / (double) quotaBytes;

        return CloudStorageSummaryResponse.builder()
                .totalBytes(activeBytes)
                .activeBytes(activeBytes)
                .trashBytes(trashBytes)
                .usedBytes(usedBytes)
                .quotaBytes(quotaBytes)
                .remainingBytes(remainingBytes)
                .usagePercent(usagePercent)
                .totalFiles(cloudFileRepository.countByOwnerIdAndDeletedAtIsNullAndIsFolderFalse(currentUserId))
                .totalFolders(cloudFileRepository.countByOwnerIdAndDeletedAtIsNullAndIsFolderTrue(currentUserId))
                .byType(byType)
                .build();
    }

    private CloudFileResponse toResponse(CloudFile cloudFile) {
        return CloudFileResponse.builder()
                .id(cloudFile.getId())
                .parentFolderId(cloudFile.getParentFolderId())
                .name(cloudFile.getName())
                .originalFileName(cloudFile.getOriginalFileName())
                .mimeType(cloudFile.getMimeType())
                .fileExtension(cloudFile.getFileExtension())
                .fileSize(cloudFile.getFileSize())
                .fileType(cloudFile.getFileType() != null ? cloudFile.getFileType().name() : null)
                .fileUrl(cloudFile.getFileUrl())
                .storageKey(cloudFile.getStorageKey())
                .manualContent(cloudFile.getManualContent())
                .isFolder(cloudFile.isFolder())
                .deletedAt(cloudFile.getDeletedAt())
                .createdAt(cloudFile.getCreatedAt())
                .updatedAt(cloudFile.getUpdatedAt())
                .build();
    }

    private CloudFile getOwnedFile(UUID ownerId, UUID fileId) {
        CloudFile cloudFile = cloudFileRepository.findById(fileId)
                .orElseThrow(() -> new NotFoundException("Cloud file not found"));
        if (!ownerId.equals(cloudFile.getOwnerId())) {
            throw new ForbiddenException("You do not have access to this cloud file");
        }
        return cloudFile;
    }

    private CloudFile getOwnedActiveFile(UUID ownerId, UUID fileId) {
        CloudFile cloudFile = getOwnedFile(ownerId, fileId);
        if (cloudFile.getDeletedAt() != null) {
            throw new NotFoundException("Cloud file not found");
        }
        return cloudFile;
    }

    private void validateParentFolder(UUID ownerId, UUID parentFolderId) {
        if (parentFolderId == null) {
            return;
        }

        CloudFile parentFolder = cloudFileRepository.findById(parentFolderId)
                .orElseThrow(() -> new NotFoundException("Parent folder not found"));
        if (parentFolder.getDeletedAt() != null) {
            throw new NotFoundException("Parent folder not found");
        }
        if (!ownerId.equals(parentFolder.getOwnerId())) {
            throw new ForbiddenException("Parent folder does not belong to current user");
        }
        if (!parentFolder.isFolder()) {
            throw new BusinessException("Parent item must be a folder");
        }
    }

    private CloudFileType normalizeFileType(String typeCode) {
        String normalizedCode = trimToNull(typeCode);
        if (normalizedCode == null) {
            return null;
        }
        return CloudFileType.fromCode(normalizedCode)
                .orElseThrow(() -> new BusinessException("Invalid cloud file type"));
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String normalizeName(String name) {
        String normalizedName = trimToNull(name);
        if (normalizedName == null) {
            throw new BusinessException("Name is required");
        }
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("Name must be at most 255 characters");
        }
        if (normalizedName.contains("/") || normalizedName.contains("\\")) {
            throw new BusinessException("Name contains invalid characters");
        }
        return normalizedName;
    }

    private String normalizeUrl(String url) {
        String normalizedUrl = trimToNull(url);
        if (normalizedUrl == null) {
            throw new BusinessException("URL is required");
        }
        String lowerCaseUrl = normalizedUrl.toLowerCase(Locale.ROOT);
        if (!lowerCaseUrl.startsWith("http://") && !lowerCaseUrl.startsWith("https://")) {
            throw new BusinessException("URL must start with http:// or https://");
        }
        if (normalizedUrl.length() > 2000) {
            throw new BusinessException("URL must be at most 2000 characters");
        }
        return normalizedUrl;
    }

    private String sanitizeUploadedFileName(String originalFileName) {
        String normalizedName = originalFileName == null || originalFileName.isBlank()
                ? "upload.bin"
                : originalFileName;
        String withoutPath = normalizedName.replace("\\", "/");
        int slashIndex = withoutPath.lastIndexOf('/');
        if (slashIndex >= 0) {
            withoutPath = withoutPath.substring(slashIndex + 1);
        }

        String cleaned = withoutPath.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) {
            cleaned = "upload.bin";
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            String extension = CloudFileType.extractExtension(cleaned);
            if (extension.isEmpty()) {
                return cleaned.substring(0, MAX_NAME_LENGTH);
            }
            int maxBaseLength = MAX_NAME_LENGTH - extension.length() - 1;
            if (maxBaseLength <= 0) {
                return cleaned.substring(0, MAX_NAME_LENGTH);
            }
            String baseName = cleaned.substring(0, Math.min(cleaned.lastIndexOf('.'), maxBaseLength));
            return baseName + "." + extension;
        }
        return cleaned;
    }

    private MessageAttachmentPayload toMessageAttachmentPayload(CloudFile cloudFile, MessageType messageType) {
        String fileUrl = normalizeNullableText(cloudFile.getFileUrl());
        if (fileUrl == null) {
            throw new BusinessException("Cloud file URL is missing");
        }

        MessageAttachmentPayload payload = new MessageAttachmentPayload();
        payload.setUrl(fileUrl);
        payload.setStorageKey(normalizeNullableText(cloudFile.getStorageKey()));
        payload.setFileName(resolveMessageFileName(cloudFile));
        payload.setContentType(normalizeNullableText(cloudFile.getMimeType()));
        payload.setFileSize(cloudFile.getFileSize() != null ? cloudFile.getFileSize() : 0L);
        payload.setType(messageType);

        if (messageType == MessageType.AUDIO) {
            payload.setDurationMs(cloudFile.getDurationMs());
            payload.setWaveform(deserializeWaveform(cloudFile.getWaveform()));
            payload.setAudioFormat(normalizeNullableText(cloudFile.getAudioFormat()));
        }

        return payload;
    }

    private String resolveMessageFileName(CloudFile cloudFile) {
        String originalFileName = normalizeNullableText(cloudFile.getOriginalFileName());
        if (originalFileName != null) {
            return originalFileName;
        }
        String cloudName = normalizeNullableText(cloudFile.getName());
        if (cloudName != null) {
            return cloudName;
        }
        return "cloud-file";
    }

    private MessageType mapCloudFileTypeToMessageType(CloudFileType cloudFileType) {
        if (cloudFileType == null) {
            return MessageType.FILE;
        }
        return switch (cloudFileType) {
            case IMAGE -> MessageType.IMAGE;
            case VIDEO -> MessageType.VIDEO;
            case AUDIO -> MessageType.AUDIO;
            default -> MessageType.FILE;
        };
    }

    private void ensureImageAnalysisTarget(CloudFile cloudFile) {
        if (cloudFile.isFolder() || cloudFile.getFileType() != CloudFileType.IMAGE) {
            throw new BusinessException("Only image files can be analyzed");
        }
    }

    private CloudFileAnalysisResponse buildAnalysisResponse(CloudFile cloudFile) {
        boolean analyzed = cloudFile.getAnalyzedAt() != null;
        Map<String, Object> rawAnalysis = analyzed
                ? Map.of(
                "disease", cloudFile.getDetectedDisease(),
                "severity", cloudFile.getSeverityLevel(),
                "confidence", cloudFile.getAnalysisConfidence())
                : Map.of();

        return CloudFileAnalysisResponse.builder()
                .fileId(cloudFile.getId())
                .mediaUrl(cloudFile.getFileUrl())
                .timeCaptured(cloudFile.getCreatedAt())
                .uploadStatus("Đã tải lên")
                .fileSizeLabel("640x480 - " + defaultLong(cloudFile.getFileSize()) + " bytes")
                .transmissionStatus("Đã gửi")
                .analysisStatus(analyzed ? cloudFile.getAnalysisStatus() : "Chưa phân tích")
                .detectedDisease(cloudFile.getDetectedDisease())
                .severityLevel(cloudFile.getSeverityLevel())
                .rawAnalysis(rawAnalysis)
                .createdAt(cloudFile.getCreatedAt())
                .updatedAt(cloudFile.getAnalyzedAt() != null ? cloudFile.getAnalyzedAt() : cloudFile.getUpdatedAt())
                .build();
    }

    private List<Double> deserializeWaveform(String waveformJson) {
        String normalized = normalizeNullableText(waveformJson);
        if (normalized == null) {
            return null;
        }
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
            return null;
        }

        String body = normalized.substring(1, normalized.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }

        String[] tokens = body.split(",");
        List<Double> values = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            try {
                values.add(Double.parseDouble(token.trim()));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return values;
    }

    private void deleteFileFromStorage(CloudFile cloudFile) {
        String storageKey = normalizeNullableText(cloudFile.getStorageKey());
        if (storageKey == null) {
            return;
        }
        s3MediaStorageService.deleteByStorageKey(storageKey);
    }

    private void ensureUploadWithinQuota(UUID ownerId, long fileSize) {
        long normalizedFileSize = Math.max(fileSize, 0L);
        long usedBytes = defaultLong(cloudFileRepository.sumUsedFileSizeByOwner(ownerId));
        long quotaBytes = getQuotaBytes(ownerId);
        long expectedUsage = safeAdd(usedBytes, normalizedFileSize);
        if (expectedUsage > quotaBytes) {
            throw new BusinessException("Dung lượng Cloud đã đầy. Vui lòng xóa bớt tệp hoặc dọn thùng rác.");
        }
    }

    private long getQuotaBytes(UUID ownerId) {
        long resolved = defaultQuotaBytes > 0 ? defaultQuotaBytes : FALLBACK_DEFAULT_QUOTA_BYTES;
        return resolved;
    }

    private long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private String buildCloudPathPrefix(UUID ownerId) {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        return String.format(
                Locale.ROOT,
                "%s/%s/%04d/%02d",
                CLOUD_PATH_PREFIX,
                ownerId,
                todayUtc.getYear(),
                todayUtc.getMonthValue());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSearchQuery(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed;
    }

    private String normalizeNullableText(String value) {
        return trimToNull(value);
    }

    private long defaultLong(Long value) {
        return Optional.ofNullable(value).orElse(0L);
    }
}
