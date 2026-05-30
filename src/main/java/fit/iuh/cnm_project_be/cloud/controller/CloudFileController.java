package fit.iuh.cnm_project_be.cloud.controller;

import fit.iuh.cnm_project_be.cloud.dto.request.CreateCloudFolderRequest;
import fit.iuh.cnm_project_be.cloud.dto.request.RenameCloudFileRequest;
import fit.iuh.cnm_project_be.cloud.dto.request.SendCloudFileToConversationRequest;
import fit.iuh.cnm_project_be.cloud.dto.response.CloudFilePageResponse;
import fit.iuh.cnm_project_be.cloud.dto.response.CloudFileResponse;
import fit.iuh.cnm_project_be.cloud.dto.response.CloudStorageSummaryResponse;
import fit.iuh.cnm_project_be.cloud.service.CloudFileService;
import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.message.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cloud")
@RequiredArgsConstructor
public class CloudFileController {

    private final CloudFileService cloudFileService;

    @GetMapping("/files")
    public ApiResponse<CloudFilePageResponse> listFiles(
            @RequestParam(required = false) UUID parentFolderId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(
                cloudFileService.listMyFiles(parentFolderId, q, type, page, size),
                UUID.randomUUID().toString());
    }

    @GetMapping("/trash")
    public ApiResponse<CloudFilePageResponse> listTrash(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(
                cloudFileService.listMyTrash(q, type, page, size),
                UUID.randomUUID().toString());
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CloudFileResponse> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) UUID parentFolderId) {
        return ApiResponse.ok(
                cloudFileService.uploadMyFile(file, parentFolderId),
                UUID.randomUUID().toString());
    }

    @PostMapping("/folders")
    public ApiResponse<CloudFileResponse> createFolder(
            @Valid @RequestBody CreateCloudFolderRequest request) {
        return ApiResponse.ok(
                cloudFileService.createFolder(request),
                UUID.randomUUID().toString());
    }

    @PatchMapping("/files/{fileId}")
    public ApiResponse<CloudFileResponse> renameFile(
            @PathVariable UUID fileId,
            @Valid @RequestBody RenameCloudFileRequest request) {
        return ApiResponse.ok(
                cloudFileService.rename(fileId, request),
                UUID.randomUUID().toString());
    }

    @DeleteMapping("/files/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable UUID fileId) {
        cloudFileService.delete(fileId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PostMapping("/files/{fileId}/restore")
    public ApiResponse<CloudFileResponse> restoreFile(@PathVariable UUID fileId) {
        return ApiResponse.ok(
                cloudFileService.restore(fileId),
                UUID.randomUUID().toString());
    }

    @DeleteMapping("/files/{fileId}/permanent")
    public ApiResponse<Void> permanentlyDeleteFile(@PathVariable UUID fileId) {
        cloudFileService.permanentlyDelete(fileId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PostMapping("/files/{fileId}/send-to-conversation")
    public ApiResponse<MessageResponse> sendFileToConversation(
            @PathVariable UUID fileId,
            @Valid @RequestBody SendCloudFileToConversationRequest request) {
        return ApiResponse.ok(
                cloudFileService.sendFileToConversation(fileId, request),
                UUID.randomUUID().toString());
    }

    @GetMapping("/storage/summary")
    public ApiResponse<CloudStorageSummaryResponse> getStorageSummary() {
        return ApiResponse.ok(
                cloudFileService.getSummary(),
                UUID.randomUUID().toString());
    }
}
