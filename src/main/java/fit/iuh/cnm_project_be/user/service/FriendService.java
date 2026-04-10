package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.user.dto.response.FriendRequestResponse;
import fit.iuh.cnm_project_be.user.dto.response.FriendshipResponse;
import fit.iuh.cnm_project_be.user.dto.response.UserSearchResponse;
import fit.iuh.cnm_project_be.user.entity.FriendRequest;
import fit.iuh.cnm_project_be.user.entity.Friendship;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.enums.FriendRelationshipStatus;
import fit.iuh.cnm_project_be.user.enums.FriendRequestStatus;
import fit.iuh.cnm_project_be.user.mapper.FriendMapper;
import fit.iuh.cnm_project_be.user.repository.FriendRequestRepository;
import fit.iuh.cnm_project_be.user.repository.FriendshipRepository;
import fit.iuh.cnm_project_be.user.repository.UserBlockRepository;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendService {
    private static final int SEARCH_LIMIT = 20;

    private final UserService userService;
    private final UserProfileRepository userProfileRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserBlockRepository userBlockRepository;
    private final FriendMapper friendMapper;


//    Tìm user
    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(String keyword) {
        UserProfile currentUser = userService.getMyProfile();

        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        return userProfileRepository.searchUsers(
                        currentUser.getUserId(),
                        keyword.trim(),
                        PageRequest.of(0, SEARCH_LIMIT)
                ).stream()
                .map(user -> friendMapper.toSearchResponse(
                        user,
                        resolveRelationshipStatus(currentUser.getUserId(), user.getUserId())
                ))
                .toList();
    }

//Gửi lời mời kết bạn
    @Transactional
    public FriendRequestResponse sendRequest(UUID receiverId) {
        UserProfile sender = userService.getMyProfile();
        UserProfile receiver = userService.getUser(receiverId);

        if (sender.getUserId().equals(receiver.getUserId())) {
            throw new BusinessException("You cannot send a friend request to yourself");
        }

        ensureUsersAreNotBlocked(sender.getUserId(), receiver.getUserId());

        if (areFriends(sender.getUserId(), receiver.getUserId())) {
            throw new BusinessException("You are already friends with this user");
        }

        if (friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(
                sender.getUserId(), receiver.getUserId(), FriendRequestStatus.PENDING)) {
            throw new BusinessException("Friend request already sent");
        }

        if (friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(
                receiver.getUserId(), sender.getUserId(), FriendRequestStatus.PENDING)) {
            throw new BusinessException("This user already sent you a friend request");
        }

        FriendRequest request = new FriendRequest();
        request.setSenderId(sender.getUserId());
        request.setReceiverId(receiver.getUserId());
        request.setStatus(FriendRequestStatus.PENDING);
        request.setCreatedAt(Instant.now());
        request.setUpdatedAt(Instant.now());

        return friendMapper.toRequestResponse(friendRequestRepository.save(request), sender, receiver);
    }

//    Lấy danh sách lời mời kết bạn đến
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getIncomingRequests() {
        UserProfile currentUser = userService.getMyProfile();

        return friendRequestRepository
                .findByReceiverIdAndStatusOrderByCreatedAtDesc(currentUser.getUserId(), FriendRequestStatus.PENDING)
                .stream()
                .map(request -> friendMapper.toRequestResponse(
                        request,
                        userService.getUser(request.getSenderId()),
                        currentUser
                ))
                .toList();
    }
    //    Lấy danh sách lời mời kết bạn đi
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getOutgoingRequests() {
        UserProfile currentUser = userService.getMyProfile();

        return friendRequestRepository
                .findBySenderIdAndStatusOrderByCreatedAtDesc(currentUser.getUserId(), FriendRequestStatus.PENDING)
                .stream()
                .map(request -> friendMapper.toRequestResponse(
                        request,
                        currentUser,
                        userService.getUser(request.getReceiverId())
                ))
                .toList();
    }
//    Chấp nhận lời mời kết bạn
    @Transactional
    public FriendRequestResponse acceptRequest(Long requestId) {
        UserProfile currentUser = userService.getMyProfile();

        FriendRequest request = friendRequestRepository.findByIdAndReceiverId(requestId, currentUser.getUserId())
                .orElseThrow(() -> new NotFoundException("Friend request not found"));

        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw new BusinessException("Request is no longer pending");
        }

        ensureUsersAreNotBlocked(request.getSenderId(), request.getReceiverId());

        request.setStatus(FriendRequestStatus.ACCEPTED);
        request.setUpdatedAt(Instant.now());
        FriendRequest savedRequest = friendRequestRepository.save(request);

        if (!areFriends(request.getSenderId(), request.getReceiverId())) {
            friendshipRepository.save(buildFriendship(request.getSenderId(), request.getReceiverId()));
            friendshipRepository.save(buildFriendship(request.getReceiverId(), request.getSenderId()));
        }

        return friendMapper.toRequestResponse(
                savedRequest,
                userService.getUser(savedRequest.getSenderId()),
                currentUser
        );
    }
//    Từ chối lời mời kết bạn
    @Transactional
    public FriendRequestResponse rejectRequest(Long requestId) {
        UserProfile currentUser = userService.getMyProfile();

        FriendRequest request = friendRequestRepository.findByIdAndReceiverId(requestId, currentUser.getUserId())
                .orElseThrow(() -> new NotFoundException("Friend request not found"));

        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw new BusinessException("Request is no longer pending");
        }

        request.setStatus(FriendRequestStatus.REJECTED);
        request.setUpdatedAt(Instant.now());

        FriendRequest savedRequest = friendRequestRepository.save(request);
        return friendMapper.toRequestResponse(
                savedRequest,
                userService.getUser(savedRequest.getSenderId()),
                currentUser
        );
    }
//    Lấy danh sách bạn bè
    @Transactional(readOnly = true)
    public List<FriendshipResponse> getFriends() {
        UserProfile currentUser = userService.getMyProfile();

        return friendshipRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUser.getUserId())
                .stream()
                .map(friendship -> friendMapper.toFriendshipResponse(
                        friendship,
                        userService.getUser(friendship.getFriendId())
                ))
                .toList();
    }
//    Giải quyết trạng thái quan hệ giữa 2 user
    private FriendRelationshipStatus resolveRelationshipStatus(UUID currentUserId, UUID targetUserId) {
        if (areFriends(currentUserId, targetUserId)) {
            return FriendRelationshipStatus.FRIEND;
        }

        if (friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(
                currentUserId, targetUserId, FriendRequestStatus.PENDING)) {
            return FriendRelationshipStatus.REQUEST_SENT;
        }

        if (friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(
                targetUserId, currentUserId, FriendRequestStatus.PENDING)) {
            return FriendRelationshipStatus.REQUEST_RECEIVED;
        }

        return FriendRelationshipStatus.NONE;
    }
//    Kiểm tra xem 2 user đã là bạn bè chưa
    private boolean areFriends(UUID userA, UUID userB) {
        return friendshipRepository.existsByUserIdAndFriendIdAndDeletedAtIsNull(userA, userB)
                || friendshipRepository.existsByUserIdAndFriendIdAndDeletedAtIsNull(userB, userA);
    }
//    Đảm bảo rằng 2 user không chặn nhau
    private void ensureUsersAreNotBlocked(UUID userA, UUID userB) {
        boolean isBlocked = userBlockRepository.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(userA, userB)
                || userBlockRepository.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(userB, userA);

        if (isBlocked) {
            throw new BusinessException("Cannot perform this action because one user has blocked the other");
        }
    }
//    Xây dựng entity Friendship
    private Friendship buildFriendship(UUID userId, UUID friendId) {
        Friendship friendship = new Friendship();
        friendship.setUserId(userId);
        friendship.setFriendId(friendId);
        return friendship;
    }

    @Transactional
    public void unfriend(UUID friendUserId) {
        UserProfile currentUser = userService.getMyProfile();

        if (currentUser.getUserId().equals(friendUserId)) {
            throw new BusinessException("You cannot unfriend yourself");
        }

        Friendship mySide = friendshipRepository
                .findByUserIdAndFriendIdAndDeletedAtIsNull(currentUser.getUserId(), friendUserId)
                .orElseThrow(() -> new BusinessException("You are not friends with this user"));

        Friendship otherSide = friendshipRepository
                .findByUserIdAndFriendIdAndDeletedAtIsNull(friendUserId, currentUser.getUserId())
                .orElseThrow(() -> new BusinessException("Friendship data is inconsistent"));

        friendshipRepository.delete(mySide);
        friendshipRepository.delete(otherSide);
    }

}
