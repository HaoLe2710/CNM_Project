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
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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
        return upload(userId, file, properties.getPathPrefix());
    }

    public UploadAttachmentResponse upload(UUID userId, MultipartFile file, String pathPrefix) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Attachment file is required");
        }
        if (resolveBucket() == null) {
            throw new BusinessException("S3 bucket is not configured");
        }
        if (resolveRegion() == null) {
            throw new BusinessException("S3 region is not configured");
        }

        validateFileSize(file);

        String resolvedContentType = resolveContentType(file);
        MessageType resolvedMessageType = resolveMessageType(resolvedContentType, file.getOriginalFilename());
        String storageKey = buildStorageKey(userId, file.getOriginalFilename(), pathPrefix);

        try (S3Client s3Client = buildClient()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(resolveBucket())
                    .key(storageKey)
                    .contentType(resolvedContentType)
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
                .contentType(resolvedContentType)
                .fileSize(file.getSize())
                .type(resolvedMessageType)
                .build();
    }

    public UploadAttachmentResponse uploadBytes(
            UUID userId,
            byte[] bytes,
            String fileName,
            String contentType,
            String pathPrefix) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("Attachment content is required");
        }
        if (resolveBucket() == null) {
            throw new BusinessException("S3 bucket is not configured");
        }
        if (resolveRegion() == null) {
            throw new BusinessException("S3 region is not configured");
        }

        String resolvedFileName = fileName == null || fileName.isBlank() ? DEFAULT_FILE_NAME : fileName;
        String resolvedContentType = resolveContentType(contentType, resolvedFileName);
        MessageType resolvedMessageType = resolveMessageType(resolvedContentType, resolvedFileName);
        validateFileSize(bytes.length, resolvedMessageType);
        String storageKey = buildStorageKey(userId, resolvedFileName, pathPrefix);

        try (S3Client s3Client = buildClient()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(resolveBucket())
                    .key(storageKey)
                    .contentType(resolvedContentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (Exception ex) {
            throw new BusinessException("Failed to upload attachment");
        }

        return UploadAttachmentResponse.builder()
                .url(resolvePublicUrl(storageKey))
                .storageKey(storageKey)
                .fileName(resolvedFileName)
                .contentType(resolvedContentType)
                .fileSize((long) bytes.length)
                .type(resolvedMessageType)
                .build();
    }

    public void deleteByStorageKey(String storageKey) {
        String normalizedStorageKey = trimToNull(storageKey);
        if (normalizedStorageKey == null) {
            return;
        }
        if (resolveBucket() == null) {
            throw new BusinessException("S3 bucket is not configured");
        }
        if (resolveRegion() == null) {
            throw new BusinessException("S3 region is not configured");
        }

        try (S3Client s3Client = buildClient()) {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(resolveBucket())
                    .key(normalizedStorageKey)
                    .build();
            s3Client.deleteObject(request);
        } catch (Exception ex) {
            throw new BusinessException("Failed to delete cloud file from storage");
        }
    }

    public byte[] downloadByStorageKey(String storageKey) {
        String normalizedStorageKey = trimToNull(storageKey);
        if (normalizedStorageKey == null) {
            throw new BusinessException("Storage key is required");
        }
        if (resolveBucket() == null) {
            throw new BusinessException("S3 bucket is not configured");
        }
        if (resolveRegion() == null) {
            throw new BusinessException("S3 region is not configured");
        }

        try (S3Client s3Client = buildClient()) {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(resolveBucket())
                    .key(normalizedStorageKey)
                    .build();
            ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(request);
            byte[] bytes = responseBytes != null ? responseBytes.asByteArray() : null;
            if (bytes == null || bytes.length == 0) {
                throw new BusinessException("Attachment content is empty");
            }
            return bytes;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Failed to download attachment");
        }
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

    private String buildStorageKey(UUID userId, String originalFileName, String pathPrefix) {
        String fileName = originalFileName == null || originalFileName.isBlank() ? DEFAULT_FILE_NAME : originalFileName;
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String normalizedPrefix = normalizePathPrefix(pathPrefix);
        return normalizedPrefix + "/" + userId + "/" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + sanitized;
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

    private MessageType resolveMessageType(String contentType, String fileName) {
        if (contentType == null || contentType.isBlank()) {
            return resolveMessageTypeByExtension(fileName);
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
        return resolveMessageTypeByExtension(fileName);
    }

    private void validateFileSize(MultipartFile file) {
        MessageType messageType = resolveMessageType(resolveContentType(file), file.getOriginalFilename());
        long fileSize = file.getSize();
        validateFileSize(fileSize, messageType);
    }

    private void validateFileSize(long fileSize, MessageType messageType) {
        long maxSize = switch (messageType) {
            case IMAGE -> properties.getMaxImageSizeBytes();
            case VIDEO -> properties.getMaxVideoSizeBytes();
            default -> properties.getMaxFileSizeBytes();
        };

        if (fileSize > maxSize) {
            throw new BusinessException("File exceeds the allowed size of " + toMegabytes(maxSize) + "MB");
        }
    }

    private long toMegabytes(long bytes) {
        return bytes / (1024 * 1024);
    }

    private String resolveContentType(MultipartFile file) {
        return resolveContentType(file != null ? file.getContentType() : null, file != null ? file.getOriginalFilename() : null);
    }

    private String resolveContentType(String contentType, String fileName) {
        if (contentType != null && !contentType.isBlank() && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType;
        }
        if (fileName == null) {
            return contentType;
        }
        String normalized = fileName.toLowerCase();
        if (normalized.endsWith(".heic")) {
            return "image/heic";
        }
        if (normalized.endsWith(".heif")) {
            return "image/heif";
        }
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        if (normalized.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (normalized.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (normalized.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (normalized.endsWith(".wav")) {
            return "audio/wav";
        }
        if (normalized.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (normalized.endsWith(".m4a")) {
            return "audio/mp4";
        }
        return contentType;
    }

    private MessageType resolveMessageTypeByExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return MessageType.FILE;
        }
        String normalized = fileName.toLowerCase();
        if (normalized.endsWith(".jpg")
                || normalized.endsWith(".jpeg")
                || normalized.endsWith(".png")
                || normalized.endsWith(".webp")
                || normalized.endsWith(".heic")
                || normalized.endsWith(".heif")) {
            return MessageType.IMAGE;
        }
        if (normalized.endsWith(".mp4")
                || normalized.endsWith(".mov")
                || normalized.endsWith(".webm")
                || normalized.endsWith(".m4v")) {
            return MessageType.VIDEO;
        }
        if (normalized.endsWith(".mp3")
                || normalized.endsWith(".wav")
                || normalized.endsWith(".ogg")
                || normalized.endsWith(".m4a")
                || normalized.endsWith(".aac")
                || normalized.endsWith(".opus")
                || normalized.endsWith(".flac")) {
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

    private String normalizePathPrefix(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return properties.getPathPrefix();
        }
        return normalized.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
