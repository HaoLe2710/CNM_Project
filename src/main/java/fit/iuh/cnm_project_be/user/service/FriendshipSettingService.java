package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.user.dto.request.UpdateFriendshipSettingRequest;
import fit.iuh.cnm_project_be.user.dto.response.FriendshipResponse;
import fit.iuh.cnm_project_be.user.dto.response.FriendshipSettingResponse;
import fit.iuh.cnm_project_be.user.entity.Friendship;
import fit.iuh.cnm_project_be.user.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendshipSettingService {

    private final FriendshipRepository friendshipRepository;
    private final FriendService friendService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<FriendshipSettingResponse> getMyFriendshipSettings() {
        UUID currentUserId = userService.getCurrentUserId();
        return friendshipRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(this::toSettingResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FriendshipSettingResponse getMyFriendshipSetting(UUID friendId) {
        UUID currentUserId = userService.getCurrentUserId();
        return toSettingResponse(getActiveFriendshipOrThrow(currentUserId, friendId));
    }

    @Transactional
    public FriendshipSettingResponse updateMyFriendshipSetting(
            UUID friendId,
            UpdateFriendshipSettingRequest request) {
        UUID currentUserId = userService.getCurrentUserId();
        Friendship friendship = getActiveFriendshipOrThrow(currentUserId, friendId);

        if (request != null) {
            if (request.getIsCloseFriend() != null) {
                friendship.setCloseFriend(request.getIsCloseFriend());
            }

            if (request.getNote() != null) {
                friendship.setCloseFriendNote(normalizeNote(request.getNote()));
            }
        }

        Friendship saved = friendshipRepository.save(friendship);
        return toSettingResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> listMyCloseFriends() {
        return friendService.getFriends(true);
    }

    private Friendship getActiveFriendshipOrThrow(UUID currentUserId, UUID friendId) {
        if (currentUserId != null && currentUserId.equals(friendId)) {
            throw new BusinessException("You cannot update close friend setting for yourself");
        }

        return friendshipRepository.findByUserIdAndFriendIdAndDeletedAtIsNull(currentUserId, friendId)
                .orElseThrow(() -> new ForbiddenException("Target user is not your active friend"));
    }

    private FriendshipSettingResponse toSettingResponse(Friendship friendship) {
        return FriendshipSettingResponse.builder()
                .userId(friendship.getUserId())
                .friendId(friendship.getFriendId())
                .isCloseFriend(friendship.isCloseFriend())
                .note(friendship.getCloseFriendNote())
                .createdAt(friendship.getCreatedAt())
                .updatedAt(friendship.getUpdatedAt())
                .build();
    }

    private String normalizeNote(String note) {
        String normalized = note == null ? null : note.trim();
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
