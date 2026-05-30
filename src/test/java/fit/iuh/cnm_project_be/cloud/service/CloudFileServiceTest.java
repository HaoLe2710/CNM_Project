package fit.iuh.cnm_project_be.cloud.service;

import fit.iuh.cnm_project_be.cloud.dto.request.CreateCloudFolderRequest;
import fit.iuh.cnm_project_be.cloud.dto.request.RenameCloudFileRequest;
import fit.iuh.cnm_project_be.cloud.dto.request.SendCloudFileToConversationRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloudFileServiceTest {

    @Mock
    private CloudFileRepository cloudFileRepository;
    @Mock
    private S3MediaStorageService s3MediaStorageService;
    @Mock
    private MessageService messageService;
    @Mock
    private UserService userService;

    @InjectMocks
    private CloudFileService cloudFileService;

    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
    }

    @Test
    void uploadCloudFileSuccess() {
        MultipartFile file = mockMultipartFile("study-notes.pdf", "application/pdf", false, 4_096L);
        when(s3MediaStorageService.upload(eq(currentUserId), eq(file), any(String.class)))
                .thenReturn(UploadAttachmentResponse.builder()
                        .url("https://cdn.example.com/cloud/study-notes.pdf")
                        .storageKey("cloud/" + currentUserId + "/2026/05/study-notes.pdf")
                        .fileName("study-notes.pdf")
                        .contentType("application/pdf")
                        .fileSize(4_096L)
                        .build());
        when(cloudFileRepository.save(any(CloudFile.class))).thenAnswer(invocation -> {
            CloudFile fileEntity = invocation.getArgument(0);
            fileEntity.setId(UUID.randomUUID());
            fileEntity.setCreatedAt(Instant.now());
            fileEntity.setUpdatedAt(Instant.now());
            return fileEntity;
        });

        CloudFileResponse response = cloudFileService.uploadMyFile(file, null);

        assertThat(response.getFileType()).isEqualTo(CloudFileType.DOCUMENT.name());
        assertThat(response.getFileUrl()).isEqualTo("https://cdn.example.com/cloud/study-notes.pdf");
        assertThat(response.isFolder()).isFalse();

        ArgumentCaptor<CloudFile> cloudFileCaptor = ArgumentCaptor.forClass(CloudFile.class);
        verify(cloudFileRepository).save(cloudFileCaptor.capture());
        assertThat(cloudFileCaptor.getValue().getOwnerId()).isEqualTo(currentUserId);
        assertThat(cloudFileCaptor.getValue().getParentFolderId()).isNull();
    }

    @Test
    void uploadCloudFileEmptyRejected() {
        MultipartFile file = mockMultipartFile("empty.txt", "text/plain", true, 0L);

        assertThatThrownBy(() -> cloudFileService.uploadMyFile(file, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("required");

        verify(s3MediaStorageService, never()).upload(any(UUID.class), any(MultipartFile.class), any(String.class));
    }

    @Test
    void uploadCloudFileInvalidParentRejected() {
        MultipartFile file = mockMultipartFile("hello.txt", "text/plain", false, 16L);
        UUID parentFolderId = UUID.randomUUID();
        when(cloudFileRepository.findById(parentFolderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cloudFileService.uploadMyFile(file, parentFolderId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Parent folder");
    }

    @Test
    void uploadCloudFileParentBelongsToOtherUserForbidden() {
        MultipartFile file = mockMultipartFile("hello.txt", "text/plain", false, 16L);
        UUID parentFolderId = UUID.randomUUID();
        CloudFile folder = buildFolder(UUID.randomUUID(), parentFolderId, null, "External");
        when(cloudFileRepository.findById(parentFolderId)).thenReturn(Optional.of(folder));

        assertThatThrownBy(() -> cloudFileService.uploadMyFile(file, parentFolderId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void uploadExceedQuotaRejected() {
        MultipartFile file = mockMultipartFile("big.zip", "application/zip", false, 10_000L);
        ReflectionTestUtils.setField(cloudFileService, "defaultQuotaBytes", 5_000L);
        when(cloudFileRepository.sumUsedFileSizeByOwner(currentUserId)).thenReturn(2_000L);

        assertThatThrownBy(() -> cloudFileService.uploadMyFile(file, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Dung lượng Cloud đã đầy");

        verify(s3MediaStorageService, never()).upload(any(UUID.class), any(MultipartFile.class), any(String.class));
    }

    @Test
    void listFilesReturnsOnlyOwnerFiles() {
        CloudFile folder = buildFolder(currentUserId, UUID.randomUUID(), null, "Documents");
        CloudFile file = buildFile(currentUserId, UUID.randomUUID(), null, "notes.txt", CloudFileType.DOCUMENT, 128L);
        when(cloudFileRepository.searchActiveFiles(eq(currentUserId), eq(null), eq(null), eq(""), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(folder, file), PageRequest.of(0, 50), 2));

        CloudFilePageResponse response = cloudFileService.listMyFiles(null, null, null, 0, 50);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().stream().map(CloudFileResponse::getName))
                .containsExactly("Documents", "notes.txt");
    }

    @Test
    void listFilesFiltersByType() {
        when(cloudFileRepository.searchActiveFiles(eq(currentUserId), eq(null), eq(CloudFileType.IMAGE), eq(""), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        cloudFileService.listMyFiles(null, null, "IMAGE", 0, 20);

        verify(cloudFileRepository).searchActiveFiles(eq(currentUserId), eq(null), eq(CloudFileType.IMAGE), eq(""), any(PageRequest.class));
    }

    @Test
    void searchFilesByName() {
        when(cloudFileRepository.searchActiveFiles(eq(currentUserId), eq(null), eq(null), eq("report"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        cloudFileService.listMyFiles(null, "report", null, 0, 20);

        verify(cloudFileRepository).searchActiveFiles(eq(currentUserId), eq(null), eq(null), eq("report"), any(PageRequest.class));
    }

    @Test
    void listTrashReturnsDeletedOnly() {
        CloudFile deleted = buildFile(currentUserId, UUID.randomUUID(), null, "old.txt", CloudFileType.DOCUMENT, 120L);
        deleted.setDeletedAt(Instant.now());
        when(cloudFileRepository.searchDeletedFiles(eq(currentUserId), eq(null), eq(""), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(deleted), PageRequest.of(0, 20), 1));

        CloudFilePageResponse response = cloudFileService.listMyTrash(null, null, 0, 20);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getDeletedAt()).isNotNull();
    }

    @Test
    void createFolderSuccess() {
        CreateCloudFolderRequest request = new CreateCloudFolderRequest();
        request.setName("Tài liệu học tập");
        request.setParentFolderId(null);

        when(cloudFileRepository.save(any(CloudFile.class))).thenAnswer(invocation -> {
            CloudFile cloudFile = invocation.getArgument(0);
            cloudFile.setId(UUID.randomUUID());
            cloudFile.setCreatedAt(Instant.now());
            cloudFile.setUpdatedAt(Instant.now());
            return cloudFile;
        });

        CloudFileResponse response = cloudFileService.createFolder(request);

        assertThat(response.getFileType()).isEqualTo(CloudFileType.FOLDER.name());
        assertThat(response.isFolder()).isTrue();
    }

    @Test
    void renameFileSuccess() {
        UUID fileId = UUID.randomUUID();
        CloudFile file = buildFile(currentUserId, fileId, null, "old-name.txt", CloudFileType.DOCUMENT, 200L);
        when(cloudFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(cloudFileRepository.save(file)).thenReturn(file);

        RenameCloudFileRequest request = new RenameCloudFileRequest();
        request.setName("new-name.txt");

        CloudFileResponse response = cloudFileService.rename(fileId, request);

        assertThat(response.getName()).isEqualTo("new-name.txt");
        assertThat(file.getName()).isEqualTo("new-name.txt");
    }

    @Test
    void deleteFileSoftDeletes() {
        UUID fileId = UUID.randomUUID();
        CloudFile file = buildFile(currentUserId, fileId, null, "temp.txt", CloudFileType.DOCUMENT, 128L);
        when(cloudFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(cloudFileRepository.save(file)).thenReturn(file);

        cloudFileService.delete(fileId);

        assertThat(file.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteOtherUserFileForbidden() {
        UUID fileId = UUID.randomUUID();
        CloudFile file = buildFile(UUID.randomUUID(), fileId, null, "temp.txt", CloudFileType.DOCUMENT, 128L);
        when(cloudFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> cloudFileService.delete(fileId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteNonEmptyFolderRejected() {
        UUID folderId = UUID.randomUUID();
        CloudFile folder = buildFolder(currentUserId, folderId, null, "Folder");
        when(cloudFileRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(cloudFileRepository.existsActiveChildren(currentUserId, folderId)).thenReturn(true);

        assertThatThrownBy(() -> cloudFileService.delete(folderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void restoreDeletedFileSuccess() {
        UUID fileId = UUID.randomUUID();
        CloudFile file = buildFile(currentUserId, fileId, null, "restore.txt", CloudFileType.DOCUMENT, 128L);
        file.setDeletedAt(Instant.now());
        when(cloudFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(cloudFileRepository.save(file)).thenReturn(file);

        CloudFileResponse response = cloudFileService.restore(fileId);

        assertThat(response.getDeletedAt()).isNull();
        assertThat(file.getDeletedAt()).isNull();
    }

    @Test
    void permanentDeleteFileSuccess() {
        UUID fileId = UUID.randomUUID();
        CloudFile file = buildFile(currentUserId, fileId, null, "purge.txt", CloudFileType.DOCUMENT, 128L);
        file.setStorageKey("cloud/" + currentUserId + "/purge.txt");
        file.setDeletedAt(Instant.now());
        when(cloudFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        cloudFileService.permanentlyDelete(fileId);

        verify(s3MediaStorageService).deleteByStorageKey("cloud/" + currentUserId + "/purge.txt");
        verify(cloudFileRepository).delete(file);
    }

    @Test
    void permanentDeleteNonEmptyFolderRejected() {
        UUID folderId = UUID.randomUUID();
        CloudFile folder = buildFolder(currentUserId, folderId, null, "Folder");
        folder.setDeletedAt(Instant.now());
        when(cloudFileRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(cloudFileRepository.existsAnyChildren(currentUserId, folderId)).thenReturn(true);

        assertThatThrownBy(() -> cloudFileService.permanentlyDelete(folderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void sendCloudFileToConversationSuccessAudioPreservesMetadata() {
        UUID fileId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        CloudFile file = buildFile(currentUserId, fileId, null, "voice-note.mp3", CloudFileType.AUDIO, 2_048L);
        file.setMimeType("audio/mpeg");
        file.setStorageKey("cloud/" + currentUserId + "/voice-note.mp3");
        file.setFileUrl("https://cdn.example.com/cloud/voice-note.mp3");
        file.setDurationMs(12_000L);
        file.setWaveform("[0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,0.2,0.3,0.4,0.5,0.6,0.7,0.8]");
        file.setAudioFormat("mp3");
        when(cloudFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        MessageResponse messageResponse = MessageResponse.builder().id(10L).conversationId(conversationId).build();
        when(messageService.sendMessage(eq(currentUserId), any(SendMessageRequest.class))).thenReturn(messageResponse);

        SendCloudFileToConversationRequest request = new SendCloudFileToConversationRequest();
        request.setConversationId(conversationId);
        request.setMessage("Gửi từ Cloud");

        MessageResponse response = cloudFileService.sendFileToConversation(fileId, request);

        assertThat(response.getId()).isEqualTo(10L);

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(currentUserId), captor.capture());

        SendMessageRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getConversationId()).isEqualTo(conversationId);
        assertThat(sentRequest.getMessageType()).isEqualTo(MessageType.AUDIO);
        assertThat(sentRequest.getAttachments()).hasSize(1);
        MessageAttachmentPayload payload = sentRequest.getAttachments().getFirst();
        assertThat(payload.getType()).isEqualTo(MessageType.AUDIO);
        assertThat(payload.getDurationMs()).isEqualTo(12_000L);
        assertThat(payload.getAudioFormat()).isEqualTo("mp3");
        assertThat(payload.getWaveform()).hasSize(16);
    }

    @Test
    void sendCloudFileToConversationFolderRejected() {
        UUID fileId = UUID.randomUUID();
        CloudFile folder = buildFolder(currentUserId, fileId, null, "Folder");
        when(cloudFileRepository.findById(fileId)).thenReturn(Optional.of(folder));

        SendCloudFileToConversationRequest request = new SendCloudFileToConversationRequest();
        request.setConversationId(UUID.randomUUID());

        assertThatThrownBy(() -> cloudFileService.sendFileToConversation(fileId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Folders");
    }

    @Test
    void sendCloudFileToConversationDeletedFileRejected() {
        UUID fileId = UUID.randomUUID();
        CloudFile file = buildFile(currentUserId, fileId, null, "test.txt", CloudFileType.DOCUMENT, 128L);
        file.setDeletedAt(Instant.now());
        when(cloudFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        SendCloudFileToConversationRequest request = new SendCloudFileToConversationRequest();
        request.setConversationId(UUID.randomUUID());

        assertThatThrownBy(() -> cloudFileService.sendFileToConversation(fileId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void sendCloudFileToConversationNonOwnerForbidden() {
        UUID fileId = UUID.randomUUID();
        CloudFile file = buildFile(UUID.randomUUID(), fileId, null, "test.txt", CloudFileType.DOCUMENT, 128L);
        when(cloudFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        SendCloudFileToConversationRequest request = new SendCloudFileToConversationRequest();
        request.setConversationId(UUID.randomUUID());

        assertThatThrownBy(() -> cloudFileService.sendFileToConversation(fileId, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void storageSummaryCountsBytesTypesAndQuota() {
        ReflectionTestUtils.setField(cloudFileService, "defaultQuotaBytes", 10_000L);
        when(cloudFileRepository.sumActiveFileSizeByOwner(currentUserId)).thenReturn(6_000L);
        when(cloudFileRepository.sumTrashFileSizeByOwner(currentUserId)).thenReturn(1_500L);
        when(cloudFileRepository.sumUsedFileSizeByOwner(currentUserId)).thenReturn(7_500L);
        when(cloudFileRepository.countByOwnerIdAndDeletedAtIsNullAndIsFolderFalse(currentUserId)).thenReturn(3L);
        when(cloudFileRepository.countByOwnerIdAndDeletedAtIsNullAndIsFolderTrue(currentUserId)).thenReturn(1L);

        CloudFileRepository.CloudFileTypeUsageView imageSummary = mockUsageSummary(CloudFileType.IMAGE, 1_000L);
        CloudFileRepository.CloudFileTypeUsageView documentSummary = mockUsageSummary(CloudFileType.DOCUMENT, 5_000L);
        when(cloudFileRepository.summarizeStorageByType(currentUserId))
                .thenReturn(List.of(imageSummary, documentSummary));

        CloudStorageSummaryResponse response = cloudFileService.getSummary();

        assertThat(response.getTotalBytes()).isEqualTo(6_000L);
        assertThat(response.getActiveBytes()).isEqualTo(6_000L);
        assertThat(response.getTrashBytes()).isEqualTo(1_500L);
        assertThat(response.getUsedBytes()).isEqualTo(7_500L);
        assertThat(response.getQuotaBytes()).isEqualTo(10_000L);
        assertThat(response.getRemainingBytes()).isEqualTo(2_500L);
        assertThat(response.getUsagePercent()).isGreaterThan(0D);
        assertThat(response.getByType().get(CloudFileType.IMAGE.name())).isEqualTo(1_000L);
        assertThat(response.getByType().get(CloudFileType.DOCUMENT.name())).isEqualTo(5_000L);
        assertThat(response.getByType().get(CloudFileType.AUDIO.name())).isEqualTo(0L);
    }

    private MultipartFile mockMultipartFile(String fileName, String contentType, boolean isEmpty, long size) {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(fileName);
        when(file.getContentType()).thenReturn(contentType);
        when(file.isEmpty()).thenReturn(isEmpty);
        when(file.getSize()).thenReturn(size);
        return file;
    }

    private CloudFile buildFolder(UUID ownerId, UUID id, UUID parentFolderId, String name) {
        CloudFile cloudFile = new CloudFile();
        cloudFile.setId(id);
        cloudFile.setOwnerId(ownerId);
        cloudFile.setParentFolderId(parentFolderId);
        cloudFile.setName(name);
        cloudFile.setFileType(CloudFileType.FOLDER);
        cloudFile.setFolder(true);
        cloudFile.setCreatedAt(Instant.now());
        cloudFile.setUpdatedAt(Instant.now());
        return cloudFile;
    }

    private CloudFile buildFile(
            UUID ownerId,
            UUID id,
            UUID parentFolderId,
            String name,
            CloudFileType fileType,
            Long fileSize) {
        CloudFile cloudFile = new CloudFile();
        cloudFile.setId(id);
        cloudFile.setOwnerId(ownerId);
        cloudFile.setParentFolderId(parentFolderId);
        cloudFile.setName(name);
        cloudFile.setOriginalFileName(name);
        cloudFile.setFileType(fileType);
        cloudFile.setFileUrl("https://cdn.example.com/" + name);
        cloudFile.setStorageKey("cloud/" + ownerId + "/" + name);
        cloudFile.setFileSize(fileSize);
        cloudFile.setFolder(false);
        cloudFile.setCreatedAt(Instant.now());
        cloudFile.setUpdatedAt(Instant.now());
        return cloudFile;
    }

    private CloudFileRepository.CloudFileTypeUsageView mockUsageSummary(CloudFileType type, long bytes) {
        CloudFileRepository.CloudFileTypeUsageView view = org.mockito.Mockito.mock(CloudFileRepository.CloudFileTypeUsageView.class);
        when(view.getFileType()).thenReturn(type);
        when(view.getTotalBytes()).thenReturn(bytes);
        return view;
    }
}
