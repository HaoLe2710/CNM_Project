package fit.iuh.cnm_project_be.storage;

import fit.iuh.cnm_project_be.aws.AwsS3Properties;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message.dto.UploadAttachmentResponse;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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

    private static final String DEFAULT_FILE_NAME = "upload.bin";

    private final S3StorageProperties properties;
    private final AwsS3Properties awsS3Properties;

    public UploadAttachmentResponse upload(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Attachment file is required");
        }
        if (resolveBucket() == null) {
            throw new BusinessException("S3 bucket is not configured");
        }
        if (resolveRegion() == null) {
            throw new BusinessException("S3 region is not configured");
        }

        String storageKey = buildStorageKey(userId, file.getOriginalFilename());

        try (S3Client s3Client = buildClient()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(resolveBucket())
                    .key(storageKey)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException ex) {
            throw new BusinessException("Failed to upload attachment");
        } catch (Exception ex) {
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
                .region(Region.of(resolveRegion()));

        if (hasStaticCredentials()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    awsS3Properties.getAccessKeyId().trim(),
                    awsS3Properties.getSecretAccessKey().trim()
            );
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
            builder.forcePathStyle(true);
        }

        return builder.build();
    }

    private String buildStorageKey(UUID userId, String originalFileName) {
        String fileName = originalFileName == null || originalFileName.isBlank() ? DEFAULT_FILE_NAME : originalFileName;
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return properties.getPathPrefix() + "/" + userId + "/" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + sanitized;
    }

    private String resolvePublicUrl(String storageKey) {
        String publicBaseUrl = resolvePublicBaseUrl();
        if (publicBaseUrl != null) {
            return publicBaseUrl + "/" + storageKey;
        }
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            return properties.getEndpoint().replaceAll("/+$", "") + "/" + resolveBucket() + "/" + storageKey;
        }
        return "https://" + resolveBucket() + ".s3." + resolveRegion() + ".amazonaws.com/" + storageKey;
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

    private boolean hasStaticCredentials() {
        return hasText(awsS3Properties.getAccessKeyId()) && hasText(awsS3Properties.getSecretAccessKey());
    }

    private String resolveBucket() {
        return hasText(properties.getBucket()) ? properties.getBucket().trim() : trimToNull(awsS3Properties.getBucket());
    }

    private String resolveRegion() {
        return hasText(properties.getRegion()) ? properties.getRegion().trim() : trimToNull(awsS3Properties.getRegion());
    }

    private String resolvePublicBaseUrl() {
        if (hasText(properties.getPublicBaseUrl())) {
            return properties.getPublicBaseUrl().replaceAll("/+$", "");
        }
        if (hasText(awsS3Properties.getCloudfrontUrl())) {
            return normalizeBaseUrl(awsS3Properties.getCloudfrontUrl());
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value.trim().replaceAll("/+$", "");
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "https://" + normalized;
    }
}
