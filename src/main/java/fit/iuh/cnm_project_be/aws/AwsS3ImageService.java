package fit.iuh.cnm_project_be.aws;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsS3ImageService {

    private static final String DEFAULT_FILE_NAME = "avatar.jpg";

    private final AwsS3Properties properties;

    public String uploadImage(UUID userId, MultipartFile file) {
        validateS3Config();

        if (file == null || file.isEmpty()) {
            throw new BusinessException("Image file is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("Only image files are supported");
        }

        String storageKey = buildStorageKey(userId, file.getOriginalFilename());

        try (S3Client s3Client = buildClient()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            return resolvePublicUrl(storageKey);
        } catch (IOException ex) {
            throw new BusinessException("Failed to read image content");
        } catch (Exception ex) {
            log.error("Failed to upload image to S3. userId={}, bucket={}, key={}", userId, properties.getBucket(), storageKey, ex);
            throw new BusinessException("Failed to upload image to S3");
        }
    }

    public void deleteImage(String imageUrl) {
        validateS3Config();

        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        String storageKey = extractStorageKey(imageUrl);
        if (storageKey == null || storageKey.isBlank()) {
            log.warn("Skip deleting image because storage key cannot be extracted. imageUrl={}", imageUrl);
            return;
        }

        try (S3Client s3Client = buildClient()) {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build();
            s3Client.deleteObject(request);
        } catch (SdkClientException ex) {
            // DNS or temporary network issues should not block updating to the new avatar.
            log.warn("Skip deleting old image due to S3 connectivity issue. bucket={}, key={}, reason={}",
                    properties.getBucket(), storageKey, ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to delete image from S3. bucket={}, key={}", properties.getBucket(), storageKey, ex);
            throw new BusinessException("Failed to delete old image from S3");
        }
    }

    private S3Client buildClient() {
        var builder = S3Client.builder().region(Region.of(properties.getRegion()));

        if (hasStaticCredentials()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    properties.getAccessKeyId().trim(),
                    properties.getSecretAccessKey().trim()
            );
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    private boolean hasStaticCredentials() {
        return properties.getAccessKeyId() != null
                && !properties.getAccessKeyId().isBlank()
                && properties.getSecretAccessKey() != null
                && !properties.getSecretAccessKey().isBlank();
    }

    private void validateS3Config() {
        if (properties.getBucket() == null || properties.getBucket().isBlank()) {
            throw new BusinessException("AWS S3 bucket is not configured");
        }
        if (properties.getRegion() == null || properties.getRegion().isBlank()) {
            throw new BusinessException("AWS region is not configured");
        }
    }

    private String buildStorageKey(UUID userId, String originalFileName) {
        String fileName = (originalFileName == null || originalFileName.isBlank())
                ? DEFAULT_FILE_NAME
                : originalFileName;

        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");

        return properties.getAvatarPathPrefix()
                + "/"
                + userId
                + "/"
                + Instant.now().toEpochMilli()
                + "-"
                + UUID.randomUUID()
                + "-"
                + sanitized;
    }

    private String resolvePublicUrl(String storageKey) {
        if (properties.getCloudfrontUrl() != null && !properties.getCloudfrontUrl().isBlank()) {
            return normalizeBaseUrl(properties.getCloudfrontUrl()) + "/" + storageKey;
        }
        return "https://" + properties.getBucket() + ".s3." + properties.getRegion() + ".amazonaws.com/" + storageKey;
    }

    private String extractStorageKey(String imageUrl) {
        String normalizedUrl = imageUrl.trim();

        String cloudfrontBase = normalizeBaseUrl(properties.getCloudfrontUrl());
        if (!cloudfrontBase.isEmpty() && normalizedUrl.startsWith(cloudfrontBase + "/")) {
            return normalizedUrl.substring((cloudfrontBase + "/").length());
        }

        String cloudfrontBaseWithoutScheme = trimTrailingSlash(properties.getCloudfrontUrl());
        if (!cloudfrontBaseWithoutScheme.isEmpty() && normalizedUrl.startsWith(cloudfrontBaseWithoutScheme + "/")) {
            return normalizedUrl.substring((cloudfrontBaseWithoutScheme + "/").length());
        }

        String s3Base = "https://" + properties.getBucket() + ".s3." + properties.getRegion() + ".amazonaws.com/";
        if (normalizedUrl.startsWith(s3Base)) {
            return normalizedUrl.substring(s3Base.length());
        }

        return null;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    private String normalizeBaseUrl(String value) {
        String normalized = trimTrailingSlash(value);
        if (normalized.isEmpty()) {
            return normalized;
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "https://" + normalized;
    }
}
