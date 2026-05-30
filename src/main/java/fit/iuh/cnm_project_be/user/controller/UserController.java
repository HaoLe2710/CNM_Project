package fit.iuh.cnm_project_be.user.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.user.dto.request.UpdateUserProfileRequest;
import fit.iuh.cnm_project_be.user.dto.request.UpdateUserSettingsRequest;
import fit.iuh.cnm_project_be.user.dto.response.UserProfileResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserSearchResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserSettingsResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserStorageCleanupResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserStorageFileItemResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserStorageSummaryResponse;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.auth.service.AuthService;
import fit.iuh.cnm_project_be.notification.service.DeviceTokenService;
import fit.iuh.cnm_project_be.user.service.FriendService;
import fit.iuh.cnm_project_be.user.service.UserSettingService;
import fit.iuh.cnm_project_be.user.service.UserStorageService;
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
    UserSettingService userSettingService;
    UserStorageService userStorageService;
    DeviceTokenService deviceTokenService;

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

    @GetMapping("/settings")
    public ApiResponse<UserSettingsResponse> getUserSettings() {
        return ApiResponse.ok(userSettingService.getMySettings(), UUID.randomUUID().toString());
    }

    @PatchMapping("/settings")
    public ApiResponse<UserSettingsResponse> updateUserSettings(
            @Valid @RequestBody UpdateUserSettingsRequest request
    ) {
        return ApiResponse.ok(
                userSettingService.updateMySettings(request.getSettings()),
                UUID.randomUUID().toString()
        );
    }

    @GetMapping("/storage/summary")
    public ApiResponse<UserStorageSummaryResponse> getStorageSummary() {
        return ApiResponse.ok(userStorageService.getSummary(), UUID.randomUUID().toString());
    }

    @GetMapping("/storage/large-files")
    public ApiResponse<List<UserStorageFileItemResponse>> getLargeFiles(
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return ApiResponse.ok(userStorageService.getLargeFiles(limit == null ? 20 : limit), UUID.randomUUID().toString());
    }

    @PostMapping("/storage/cache/cleanup")
    public ApiResponse<UserStorageCleanupResponse> cleanupCache() {
        return ApiResponse.ok(userStorageService.cleanupCache(), UUID.randomUUID().toString());
    }

    @PostMapping("/storage/large-files/cleanup")
    public ApiResponse<UserStorageCleanupResponse> cleanupLargeFiles() {
        return ApiResponse.ok(userStorageService.cleanupLargeFiles(), UUID.randomUUID().toString());
    }

    @PostMapping("/storage/chat-data/cleanup")
    public ApiResponse<UserStorageCleanupResponse> cleanupChatData() {
        return ApiResponse.ok(userStorageService.cleanupChatData(), UUID.randomUUID().toString());
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
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        String token = body.get("token");
        userService.updateFcmToken(userId, token);
        deviceTokenService.registerLegacyFcmToken(
                userId,
                token,
                jwt.getClaimAsString("deviceId"),
                jwt.getClaimAsString("platform")
        );
        return ResponseEntity.ok().build();
    }
}
