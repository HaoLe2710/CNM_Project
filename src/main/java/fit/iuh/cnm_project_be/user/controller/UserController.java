package fit.iuh.cnm_project_be.user.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.user.dto.request.UpdateUserProfileRequest;
import fit.iuh.cnm_project_be.user.dto.response.UserProfileResponse;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.mapper.UserProfileMapper;
import fit.iuh.cnm_project_be.auth.service.AuthService;
import fit.iuh.cnm_project_be.user.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    UserService userService;
    UserProfileMapper userProfileMapper;
    AuthService authService;

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getUserProfile() {
        UserProfile profile = userService.getUserProfile();
        UserProfileResponse response = userProfileMapper.toResponse(profile);

        String requestId = UUID.randomUUID().toString();
        return ApiResponse.ok(response, requestId);
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateUserProfile(
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        UserProfile profile = userService.updateUserProfile(request);
        UserProfileResponse response = userProfileMapper.toResponse(profile);

        return ApiResponse.ok(response, UUID.randomUUID().toString());
    }

    @DeleteMapping("/profile")
    public ApiResponse<String> softDeleteUserAndAccount() {
        UserProfile profile = userService.getMyProfile();
        authService.softDeleteUserAndAccount(profile.getUserId());

        return ApiResponse.ok("Delete user and account successfully", UUID.randomUUID().toString());
    }
}
