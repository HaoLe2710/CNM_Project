package fit.iuh.cnm_project_be.aws.controller;

import fit.iuh.cnm_project_be.aws.dto.UploadImageResponse;
import fit.iuh.cnm_project_be.aws.service.AwsImageUploadService;
import fit.iuh.cnm_project_be.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/aws")
@RequiredArgsConstructor
public class AwsImageController {
    private final AwsImageUploadService awsImageUploadService;

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadImageResponse> uploadImage(@RequestPart("file") MultipartFile file, Principal principal) {
        // Lấy userId từ principal hoặc context (giả sử principal.getName() là userId dạng UUID)
        UUID userId = UUID.fromString(principal.getName());
        String imageUrl = awsImageUploadService.uploadImage(userId, file);
        return ApiResponse.ok(new UploadImageResponse(imageUrl), UUID.randomUUID().toString());
    }
}
