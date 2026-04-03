package fit.iuh.cnm_project_be.user.mapper;

import fit.iuh.cnm_project_be.user.dto.request.UpdateUserProfileRequest;
import fit.iuh.cnm_project_be.user.dto.response.UserProfileResponse;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class UserProfileMapper {

    public UserProfileResponse toResponse(UserProfile profile) {
        return UserProfileResponse.builder()
                .userId(profile.getUserId())
                .username(profile.getUsername())
                .displayName(profile.getDisplayName())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .avatarUrl(profile.getAvatarUrl())
                .inviteLink(profile.getInviteLink())
                .qrCodeUrl(profile.getQrCodeUrl())
                .bio(profile.getBio())
                .phone(profile.getPhone())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    public void updateEntity(UserProfile profile, UpdateUserProfileRequest request) {
        if (request.getDisplayName() != null) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (request.getFirstName() != null) {
            profile.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            profile.setLastName(request.getLastName());
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getPhone() != null) {
            profile.setPhone(request.getPhone());
        }
    }
}
