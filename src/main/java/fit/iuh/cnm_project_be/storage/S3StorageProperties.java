package fit.iuh.cnm_project_be.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage.s3")
public class S3StorageProperties {

    private String region = "ap-southeast-1";
    private String bucket;
    private String endpoint;
    private String publicBaseUrl;
    private String pathPrefix = "chat";
    private long maxImageSizeBytes = 20 * 1024 * 1024L;
    private long maxVideoSizeBytes = 50 * 1024 * 1024L;
    private long maxFileSizeBytes = 20 * 1024 * 1024L;
}
