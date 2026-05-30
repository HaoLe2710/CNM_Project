package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.user.dto.request.UpdateFriendshipSettingRequest;
import fit.iuh.cnm_project_be.user.dto.response.FriendshipResponse;
import fit.iuh.cnm_project_be.user.dto.response.FriendshipSettingResponse;
import fit.iuh.cnm_project_be.user.entity.Friendship;
import fit.iuh.cnm_project_be.user.repository.FriendshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipSettingServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private FriendService friendService;

    @Mock
    private UserService userService;

    private FriendshipSettingService friendshipSettingService;

    @BeforeEach
    void setUp() {
        friendshipSettingService = new FriendshipSettingService(
                friendshipRepository,
                friendService,
                userService
        );
    }

    @Test
    void updateSettingCloseFriendSuccess() {
        UUID currentUserId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        Friendship friendship = friendship(currentUserId, friendId, false, null);
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(friendshipRepository.findByUserIdAndFriendIdAndDeletedAtIsNull(currentUserId, friendId))
                .thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateFriendshipSettingRequest request = new UpdateFriendshipSettingRequest();
        request.setIsCloseFriend(true);
        request.setNote("Bạn thân đại học");

        FriendshipSettingResponse response = friendshipSettingService.updateMyFriendshipSetting(friendId, request);

        assertThat(response.isCloseFriend()).isTrue();
        assertThat(response.getNote()).isEqualTo("Bạn thân đại học");
        verify(friendshipRepository).save(friendship);
    }

    @Test
    void updateSettingUncloseFriendSuccess() {
        UUID currentUserId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        Friendship friendship = friendship(currentUserId, friendId, true, "Ghi chú cũ");
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(friendshipRepository.findByUserIdAndFriendIdAndDeletedAtIsNull(currentUserId, friendId))
                .thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateFriendshipSettingRequest request = new UpdateFriendshipSettingRequest();
        request.setIsCloseFriend(false);

        FriendshipSettingResponse response = friendshipSettingService.updateMyFriendshipSetting(friendId, request);

        assertThat(response.isCloseFriend()).isFalse();
        assertThat(response.getNote()).isEqualTo("Ghi chú cũ");
    }

    @Test
    void updateSettingSelfFriendForbidden() {
        UUID currentUserId = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(currentUserId);

        assertThatThrownBy(() ->
                friendshipSettingService.updateMyFriendshipSetting(currentUserId, new UpdateFriendshipSettingRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateSettingNonFriendForbidden() {
        UUID currentUserId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(friendshipRepository.findByUserIdAndFriendIdAndDeletedAtIsNull(currentUserId, friendId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                friendshipSettingService.updateMyFriendshipSetting(friendId, new UpdateFriendshipSettingRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getSettingReturnsDefaultFalseWhenFriendshipNotMarkedClose() {
        UUID currentUserId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        Friendship friendship = friendship(currentUserId, friendId, false, null);
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(friendshipRepository.findByUserIdAndFriendIdAndDeletedAtIsNull(currentUserId, friendId))
                .thenReturn(Optional.of(friendship));

        FriendshipSettingResponse response = friendshipSettingService.getMyFriendshipSetting(friendId);

        assertThat(response.isCloseFriend()).isFalse();
        assertThat(response.getNote()).isNull();
    }

    @Test
    void getMySettingsReturnsAllFriendshipSettings() {
        UUID currentUserId = UUID.randomUUID();
        UUID firstFriendId = UUID.randomUUID();
        UUID secondFriendId = UUID.randomUUID();
        Friendship first = friendship(currentUserId, firstFriendId, true, "A");
        Friendship second = friendship(currentUserId, secondFriendId, false, null);
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(friendshipRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUserId))
                .thenReturn(List.of(first, second));

        List<FriendshipSettingResponse> response = friendshipSettingService.getMyFriendshipSettings();

        assertThat(response).hasSize(2);
        assertThat(response).extracting(FriendshipSettingResponse::getFriendId)
                .containsExactly(firstFriendId, secondFriendId);
    }

    @Test
    void listCloseFriendsReturnsOnlyCloseFriendsFromFriendService() {
        FriendshipResponse first = FriendshipResponse.builder().build();
        FriendshipResponse second = FriendshipResponse.builder().build();
        when(friendService.getFriends(true)).thenReturn(List.of(first, second));

        List<FriendshipResponse> response = friendshipSettingService.listMyCloseFriends();

        assertThat(response).hasSize(2);
        verify(friendService).getFriends(true);
    }

    @Test
    void updateSettingBlankNoteNormalizesToNull() {
        UUID currentUserId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        Friendship friendship = friendship(currentUserId, friendId, true, "Old");
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(friendshipRepository.findByUserIdAndFriendIdAndDeletedAtIsNull(currentUserId, friendId))
                .thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateFriendshipSettingRequest request = new UpdateFriendshipSettingRequest();
        request.setNote("   ");

        FriendshipSettingResponse response = friendshipSettingService.updateMyFriendshipSetting(friendId, request);

        assertThat(response.getNote()).isNull();
    }

    private Friendship friendship(UUID userId, UUID friendId, boolean close, String note) {
        Friendship friendship = new Friendship();
        friendship.setUserId(userId);
        friendship.setFriendId(friendId);
        friendship.setCloseFriend(close);
        friendship.setCloseFriendNote(note);
        friendship.setCreatedAt(Instant.parse("2026-05-29T00:00:00Z"));
        friendship.setUpdatedAt(Instant.parse("2026-05-29T00:00:00Z"));
        return friendship;
    }
}
