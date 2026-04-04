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
}
