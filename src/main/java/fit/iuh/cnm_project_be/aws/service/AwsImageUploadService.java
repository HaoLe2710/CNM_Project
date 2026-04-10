package fit.iuh.cnm_project_be.aws.service;

import fit.iuh.cnm_project_be.aws.AwsS3ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AwsImageUploadService {
    private final AwsS3ImageService awsS3ImageService;

    public String uploadImage(UUID userId, MultipartFile file) {
        return awsS3ImageService.uploadImage(userId, file);
    }
}
