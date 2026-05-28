package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchResult;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.user.dto.response.FriendRequestResponse;
import fit.iuh.cnm_project_be.user.entity.FriendRequest;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.enums.FriendRequestStatus;
import fit.iuh.cnm_project_be.user.mapper.FriendMapper;
import fit.iuh.cnm_project_be.user.repository.FriendRequestRepository;
import fit.iuh.cnm_project_be.user.repository.FriendshipRepository;
import fit.iuh.cnm_project_be.user.repository.UserBlockRepository;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FriendNotificationIntegrationTest {

    @Mock
    private UserService userService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private FriendRequestRepository friendRequestRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private FriendMapper friendMapper;
    @Mock
    private NotificationDispatcher notificationDispatcher;

    private FriendService friendService;

    @BeforeEach
    void setUp() {
        friendService = new FriendService(
                userService,
                userProfileRepository,
                friendRequestRepository,
                friendshipRepository,
                userBlockRepository,
                friendMapper,
                notificationDispatcher
        );
        when(notificationDispatcher.dispatch(any())).thenReturn(dispatchResult());
    }

    @Test
    void sendRequestDispatchesFriendRequestReceived() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UserProfile sender = profile(senderId, "sender", "Sender");
        UserProfile receiver = profile(receiverId, "receiver", "Receiver");
        when(userService.getMyProfile()).thenReturn(sender);
        when(userService.getUser(receiverId)).thenReturn(receiver);
        when(friendRequestRepository.save(any(FriendRequest.class))).thenAnswer(invocation -> {
            FriendRequest request = invocation.getArgument(0);
            request.setId(10L);
            return request;
        });
        when(friendMapper.toRequestResponse(any(), any(), any())).thenReturn(FriendRequestResponse.builder().id(10L).build());

        friendService.sendRequest(receiverId);

        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.FRIEND_REQUEST_RECEIVED);
        assertThat(captor.getValue().getActorId()).isEqualTo(senderId);
        assertThat(captor.getValue().getExplicitRecipientIds()).containsExactly(receiverId);
        assertThat(captor.getValue().getDedupKeyPrefix()).isEqualTo("friend_request:10");
    }

    @Test
    void acceptRequestDispatchesFriendRequestAcceptedToOriginalSender() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        FriendRequest request = new FriendRequest();
        request.setId(20L);
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        request.setStatus(FriendRequestStatus.PENDING);
        UserProfile sender = profile(senderId, "sender", "Sender");
        UserProfile receiver = profile(receiverId, "receiver", "Receiver");
        when(userService.getMyProfile()).thenReturn(receiver);
        when(friendRequestRepository.findByIdAndReceiverId(20L, receiverId)).thenReturn(Optional.of(request));
        when(friendRequestRepository.save(any(FriendRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUser(senderId)).thenReturn(sender);
        when(friendMapper.toRequestResponse(any(), any(), any())).thenReturn(FriendRequestResponse.builder().id(20L).build());

        friendService.acceptRequest(20L);

        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.FRIEND_REQUEST_ACCEPTED);
        assertThat(captor.getValue().getActorId()).isEqualTo(receiverId);
        assertThat(captor.getValue().getExplicitRecipientIds()).containsExactly(senderId);
        assertThat(captor.getValue().getDedupKeyPrefix()).isEqualTo("friend_request:20:accepted");
    }

    private UserProfile profile(UUID userId, String username, String displayName) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUsername(username);
        profile.setDisplayName(displayName);
        return profile;
    }

    private NotificationDispatchResult dispatchResult() {
        return NotificationDispatchResult.builder()
                .candidateRecipientCount(1)
                .policyAllowedInAppCount(1)
                .policyAllowedPushCount(1)
                .createdNotificationCount(1)
                .pushSuccessCount(1)
                .pushFailureCount(0)
                .deniedRecipients(Map.of())
                .createdNotificationIds(List.of(UUID.randomUUID()))
                .errors(List.of())
                .build();
    }
}
