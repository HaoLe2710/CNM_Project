package fit.iuh.cnm_project_be.user.mapper;

import fit.iuh.cnm_project_be.user.dto.response.UserBlockResponse;
import fit.iuh.cnm_project_be.user.entity.UserBlock;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserBlockMapper {
    private final FriendMapper friendMapper;
    public UserBlockResponse toResponse(UserBlock userBlock, UserProfile blockedUser) {
        return UserBlockResponse.builder()
                .id(userBlock.getId())
                .reason(userBlock.getReason())
                .createdAt(userBlock.getCreatedAt())
                .blockedUser(friendMapper.toUserSummary(blockedUser))
                .build();
    }
}
