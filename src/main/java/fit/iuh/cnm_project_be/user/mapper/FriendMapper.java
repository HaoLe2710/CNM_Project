package fit.iuh.cnm_project_be.user.mapper;

import fit.iuh.cnm_project_be.user.dto.response.FriendRequestResponse;
import fit.iuh.cnm_project_be.user.dto.response.FriendshipResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserSearchResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserSummaryResponse;
import fit.iuh.cnm_project_be.user.entity.FriendRequest;
import fit.iuh.cnm_project_be.user.entity.Friendship;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.enums.FriendRelationshipStatus;
import org.springframework.stereotype.Component;

@Component
public class FriendMapper {
    public UserSummaryResponse toUserSummary(UserProfile profile) {
        return UserSummaryResponse.builder()
                .userId(profile.getUserId())
                .username(profile.getUsername())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .build();
    }

    public UserSearchResponse toSearchResponse(UserProfile profile, FriendRelationshipStatus relationshipStatus) {
        return UserSearchResponse.builder()
                .userId(profile.getUserId())
                .username(profile.getUsername())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .relationshipStatus(relationshipStatus)
                .build();
    }

    public FriendRequestResponse toRequestResponse(FriendRequest request, UserProfile sender, UserProfile receiver) {
        return FriendRequestResponse.builder()
                .id(request.getId())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .sender(toUserSummary(sender))
                .receiver(toUserSummary(receiver))
                .build();
    }

    public FriendshipResponse toFriendshipResponse(Friendship friendship, UserProfile friend) {
        return FriendshipResponse.builder()
                .friendshipId(friendship.getId())
                .createdAt(friendship.getCreatedAt())
                .isCloseFriend(friendship.isCloseFriend())
                .closeFriendNote(friendship.getCloseFriendNote())
                .friend(toUserSummary(friend))
                .build();
    }
}
