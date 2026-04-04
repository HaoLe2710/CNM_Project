package fit.iuh.cnm_project_be.storage;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message.dto.UploadAttachmentResponse;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3MediaStorageService {

    private final S3StorageProperties properties;

    public UploadAttachmentResponse upload(UUID userId, MultipartFile file) {
        if (properties.getBucket() == null || properties.getBucket().isBlank()) {
            throw new BusinessException("S3 bucket is not configured");
        }

        String storageKey = buildStorageKey(userId, file.getOriginalFilename());

        try (S3Client s3Client = buildClient()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException ex) {
            throw new BusinessException("Failed to upload attachment");
        }

        return UploadAttachmentResponse.builder()
                .url(resolvePublicUrl(storageKey))
                .storageKey(storageKey)
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .type(resolveMessageType(file.getContentType()))
                .build();
    }

    private S3Client buildClient() {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
            builder.forcePathStyle(true);
        }

        return builder.build();
    }

    private String buildStorageKey(UUID userId, String originalFileName) {
        String sanitized = originalFileName == null ? "upload.bin" : originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return properties.getPathPrefix() + "/" + userId + "/" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + sanitized;
    }

    private String resolvePublicUrl(String storageKey) {
        if (properties.getPublicBaseUrl() != null && !properties.getPublicBaseUrl().isBlank()) {
            return properties.getPublicBaseUrl().replaceAll("/+$", "") + "/" + storageKey;
        }
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            return properties.getEndpoint().replaceAll("/+$", "") + "/" + properties.getBucket() + "/" + storageKey;
        }
        return "https://" + properties.getBucket() + ".s3." + properties.getRegion() + ".amazonaws.com/" + storageKey;
    }

    private MessageType resolveMessageType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MessageType.FILE;
        }
        if (contentType.startsWith("image/")) {
            return MessageType.IMAGE;
        }
        if (contentType.startsWith("video/")) {
            return MessageType.VIDEO;
        }
        if (contentType.startsWith("audio/")) {
            return MessageType.AUDIO;
        }
        return MessageType.FILE;
    }
}
