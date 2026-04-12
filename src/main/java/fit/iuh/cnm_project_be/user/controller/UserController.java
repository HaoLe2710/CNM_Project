package fit.iuh.cnm_project_be.user.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.user.dto.request.UpdateUserProfileRequest;
import fit.iuh.cnm_project_be.user.dto.response.UserProfileResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserSearchResponse;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.auth.service.AuthService;
import fit.iuh.cnm_project_be.user.service.FriendService;
import fit.iuh.cnm_project_be.user.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public class UserController {

    @Autowired
    UserService userService;
    @Autowired
    AuthService authService;
    FriendService friendService;

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getUserProfile() {
        UserProfile profile = userService.getUserProfile();
        UserProfileResponse response = toResponse(profile);

        String requestId = UUID.randomUUID().toString();
        return ApiResponse.ok(response, requestId);
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateUserProfile(
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        UserProfile profile = userService.updateUserProfile(request);
        UserProfileResponse response = toResponse(profile);

        return ApiResponse.ok(response, UUID.randomUUID().toString());
    }

    @PatchMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserProfileResponse> updateProfileAvatar(
            @RequestPart("file") MultipartFile file
    ) {
        UserProfile profile = userService.updateProfileAvatar(file);
        UserProfileResponse response = toResponse(profile);
        return ApiResponse.ok(response, UUID.randomUUID().toString());
    }

    @PatchMapping(value = "/profile/cover-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserProfileResponse> updateProfileCoverImage(
            @RequestPart("file") MultipartFile file
    ) {
        UserProfile profile = userService.updateProfileCoverImage(file);
        UserProfileResponse response = toResponse(profile);
        return ApiResponse.ok(response, UUID.randomUUID().toString());
    }

    @DeleteMapping("/profile")
    public ApiResponse<String> softDeleteUserAndAccount() {
        UserProfile profile = userService.getMyProfile();
        authService.softDeleteUserAndAccount(profile.getUserId());

        return ApiResponse.ok("Delete user and account successfully", UUID.randomUUID().toString());
    }

    @GetMapping("/search")
    public ApiResponse<List<UserSearchResponse>> searchUsers(@RequestParam("q") String keyword) {
        return ApiResponse.ok(friendService.searchUsers(keyword), UUID.randomUUID().toString());
    }

    private UserProfileResponse toResponse(UserProfile profile) {
        return UserProfileResponse.builder()
            .userId(profile.getUserId())
            .username(profile.getUsername())
            .displayName(profile.getDisplayName())
            .firstName(profile.getFirstName())
            .lastName(profile.getLastName())
            .avatarUrl(profile.getAvatarUrl())
            .coverUrl(profile.getCoverUrl())
            .inviteLink(profile.getInviteLink())
            .qrCodeUrl(profile.getQrCodeUrl())
            .bio(profile.getBio())
            .phone(profile.getPhone())
            .email(profile.getEmail())
            .gender(profile.getGender())
            .dob(profile.getDob())
            .createdAt(profile.getCreatedAt())
            .updatedAt(profile.getUpdatedAt())
            .build();
    }

    @PostMapping("/me/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {
        UUID userId = UUID.fromString(jwt.getSubject()); // subject = userId???Nhớ check lại cấu hình JWT
        userService.updateFcmToken(userId, body.get("token"));
        return ResponseEntity.ok().build();
    }
}
