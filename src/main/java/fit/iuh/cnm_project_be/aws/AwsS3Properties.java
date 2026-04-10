package fit.iuh.cnm_project_be.aws;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.aws.s3")
public class AwsS3Properties {

    private String region = "ap-southeast-1";
    private String bucket;
    private String cloudfrontUrl;
    private String accessKeyId;
    private String secretAccessKey;
    private String avatarPathPrefix = "avatars";
}
