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
    private String bucket= "congnghemoi-zalo-bucket";
    private String cloudfrontUrl = "d2hm0smllhhb7h.cloudfront.net/";
    private String accessKeyId ="AKIAQA2XS6H5TTIZLOT7";
    private String secretAccessKey= "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";
    private String avatarPathPrefix = "avatars";
}
