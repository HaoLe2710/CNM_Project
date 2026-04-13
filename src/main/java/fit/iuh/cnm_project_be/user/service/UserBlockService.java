package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.user.dto.response.UserBlockResponse;
import fit.iuh.cnm_project_be.user.entity.FriendRequest;
import fit.iuh.cnm_project_be.user.entity.UserBlock;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.enums.FriendRequestStatus;
import fit.iuh.cnm_project_be.user.mapper.UserBlockMapper;
import fit.iuh.cnm_project_be.user.repository.FriendRequestRepository;
import fit.iuh.cnm_project_be.user.repository.FriendshipRepository;
import fit.iuh.cnm_project_be.user.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserBlockService {
    private final UserService userService;
    private final UserBlockRepository userBlockRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final UserBlockMapper userBlockMapper;

    @Transactional
    public UserBlockResponse blockUser(UUID blockedUserId, String reason) {
        UserProfile blocker = userService.getMyProfile();
        UserProfile blocked = userService.getUser(blockedUserId);

        if (blocker.getUserId().equals(blocked.getUserId())) {
            throw new BusinessException("You cannot block yourself");
        }

        if (userBlockRepository.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(
                blocker.getUserId(), blocked.getUserId())) {
            throw new BusinessException("You already blocked this user");
        }

        removeFriendshipIfExists(blocker.getUserId(), blocked.getUserId());
        rejectPendingRequests(blocker.getUserId(), blocked.getUserId());

        UserBlock userBlock = new UserBlock();
        userBlock.setBlockerId(blocker.getUserId());
        userBlock.setBlockedId(blocked.getUserId());
        userBlock.setReason(reason);
        userBlock.setCreatedAt(Instant.now());
        userBlock.setDeletedAt(null);

        return userBlockMapper.toResponse(userBlockRepository.save(userBlock), blocked);
    }

    @Transactional
    public void unblockUser(UUID blockedUserId) {
        UserProfile currentUser = userService.getMyProfile();

        UserBlock userBlock = userBlockRepository
                .findByBlockerIdAndBlockedIdAndDeletedAtIsNull(currentUser.getUserId(), blockedUserId)
                .orElseThrow(() -> new NotFoundException("Blocked user not found"));

        userBlock.setDeletedAt(Instant.now());
        userBlockRepository.save(userBlock);
    }

    @Transactional(readOnly = true)
    public List<UserBlockResponse> getBlockedUsers() {
        UserProfile currentUser = userService.getMyProfile();

        return userBlockRepository
                .findByBlockerIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUser.getUserId())
                .stream()
                .map(userBlock -> userBlockMapper.toResponse(
                        userBlock,
                        userService.getUser(userBlock.getBlockedId())
                ))
                .toList();
    }

    private void removeFriendshipIfExists(UUID userA, UUID userB) {
        friendshipRepository.findByUserIdAndFriendIdAndDeletedAtIsNull(userA, userB)
                .ifPresent(friendshipRepository::delete);

        friendshipRepository.findByUserIdAndFriendIdAndDeletedAtIsNull(userB, userA)
                .ifPresent(friendshipRepository::delete);
    }

    private void rejectPendingRequests(UUID userA, UUID userB) {
        rejectPendingRequestsOneWay(userA, userB);
        rejectPendingRequestsOneWay(userB, userA);
    }

    private void rejectPendingRequestsOneWay(UUID senderId, UUID receiverId) {
        List<FriendRequest> requests = friendRequestRepository
                .findBySenderIdAndReceiverIdAndStatus(senderId, receiverId, FriendRequestStatus.PENDING);

        if (requests.isEmpty()) {
            return;
        }

        requests.forEach(request -> {
            request.setStatus(FriendRequestStatus.REJECTED);
            request.setUpdatedAt(Instant.now());
        });

        friendRequestRepository.saveAll(requests);
    }
}
