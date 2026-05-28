package fit.iuh.cnm_project_be.social.service;

import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchResult;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.social.dto.request.CreatePostCommentRequest;
import fit.iuh.cnm_project_be.social.entity.Post;
import fit.iuh.cnm_project_be.social.entity.PostComment;
import fit.iuh.cnm_project_be.social.entity.PostLike;
import fit.iuh.cnm_project_be.social.enums.PostVisibilityMode;
import fit.iuh.cnm_project_be.social.repository.MomentCommentRepository;
import fit.iuh.cnm_project_be.social.repository.MomentReactionRepository;
import fit.iuh.cnm_project_be.social.repository.MomentRepository;
import fit.iuh.cnm_project_be.social.repository.MomentViewRepository;
import fit.iuh.cnm_project_be.social.repository.PostCommentLikeRepository;
import fit.iuh.cnm_project_be.social.repository.PostCommentRepository;
import fit.iuh.cnm_project_be.social.repository.PostLikeRepository;
import fit.iuh.cnm_project_be.social.repository.PostMediaRepository;
import fit.iuh.cnm_project_be.social.repository.PostRepository;
import fit.iuh.cnm_project_be.social.repository.PostTagRepository;
import fit.iuh.cnm_project_be.social.repository.PostVisibilityGrantRepository;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import fit.iuh.cnm_project_be.user.entity.Friendship;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.FriendshipRepository;
import fit.iuh.cnm_project_be.user.repository.UserBlockRepository;
import fit.iuh.cnm_project_be.user.service.UserService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialNotificationIntegrationTest {

    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private PostCommentLikeRepository postCommentLikeRepository;
    @Mock private PostMediaRepository postMediaRepository;
    @Mock private PostTagRepository postTagRepository;
    @Mock private PostVisibilityGrantRepository postVisibilityGrantRepository;
    @Mock private MomentRepository momentRepository;
    @Mock private MomentReactionRepository momentReactionRepository;
    @Mock private MomentCommentRepository momentCommentRepository;
    @Mock private MomentViewRepository momentViewRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private UserService userService;
    @Mock private S3MediaStorageService s3MediaStorageService;
    @Mock private NotificationDispatcher notificationDispatcher;

    private SocialService socialService;

    @BeforeEach
    void setUp() {
        socialService = new SocialService(
                postRepository,
                postLikeRepository,
                postCommentRepository,
                postCommentLikeRepository,
                postMediaRepository,
                postTagRepository,
                postVisibilityGrantRepository,
                momentRepository,
                momentReactionRepository,
                momentCommentRepository,
                momentViewRepository,
                friendshipRepository,
                userBlockRepository,
                userService,
                s3MediaStorageService,
                notificationDispatcher
        );
        lenient().when(notificationDispatcher.dispatch(any())).thenReturn(dispatchResult());
        lenient().when(userBlockRepository.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(any(), any())).thenReturn(false);
        lenient().when(friendshipRepository.existsByUserIdAndFriendIdAndDeletedAtIsNull(any(), any())).thenReturn(true);
        lenient().when(postMediaRepository.findByPostIdOrderBySortOrderAscCreatedAtAsc(any())).thenReturn(List.of());
        lenient().when(postTagRepository.findByPostId(any())).thenReturn(List.of());
        lenient().when(postLikeRepository.countByPostId(any())).thenReturn(0L);
        lenient().when(postCommentRepository.countByPostIdAndDeletedAtIsNull(any())).thenReturn(0L);
        lenient().when(postLikeRepository.existsByPostIdAndUserId(any(), any())).thenReturn(false);
        lenient().when(postCommentLikeRepository.countByCommentId(any())).thenReturn(0L);
        lenient().when(postCommentLikeRepository.existsByCommentIdAndUserId(any(), any())).thenReturn(false);
    }

    @Test
    void likePostDispatchesPostReactionToPostAuthor() {
        UUID actorId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post post = post(postId, ownerId);
        when(userService.getMyProfile()).thenReturn(profile(actorId, "actor", "Actor"));
        when(userService.getUser(ownerId)).thenReturn(profile(ownerId, "owner", "Owner"));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postLikeRepository.save(any(PostLike.class))).thenAnswer(invocation -> {
            PostLike like = invocation.getArgument(0);
            like.setId(UUID.randomUUID());
            return like;
        });

        socialService.likePost(postId);

        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.POST_REACTION);
        assertThat(captor.getValue().getActorId()).isEqualTo(actorId);
        assertThat(captor.getValue().getExplicitRecipientIds()).containsExactly(ownerId);
        assertThat(captor.getValue().getDedupKeyPrefix()).startsWith("reaction:");
    }

    @Test
    void addCommentDispatchesCommentMentionAndPostCommentWithoutDuplicateRecipient() {
        UUID actorId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mentionedId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Post post = post(postId, ownerId);
        when(userService.getMyProfile()).thenReturn(profile(actorId, "actor", "Actor"));
        when(userService.getUser(actorId)).thenReturn(profile(actorId, "actor", "Actor"));
        when(userService.getUser(ownerId)).thenReturn(profile(ownerId, "owner", "Owner"));
        when(userService.getUser(mentionedId)).thenReturn(profile(mentionedId, "mentioned", "Mentioned"));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(friendshipRepository.findByUserIdAndDeletedAtIsNull(actorId)).thenReturn(List.of(friendship(actorId, mentionedId)));
        when(postCommentRepository.save(any(PostComment.class))).thenAnswer(invocation -> {
            PostComment comment = invocation.getArgument(0);
            comment.setId(commentId);
            return comment;
        });
        CreatePostCommentRequest request = new CreatePostCommentRequest();
        request.setContent("hello @mentioned");

        socialService.addComment(postId, request);

        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatcher, org.mockito.Mockito.times(2)).dispatch(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationDispatchRequest::getType)
                .containsExactly(NotificationType.COMMENT_MENTION, NotificationType.POST_COMMENT);
        assertThat(captor.getAllValues().get(0).getExplicitRecipientIds()).containsExactly(mentionedId);
        assertThat(captor.getAllValues().get(1).getExplicitRecipientIds()).containsExactly(ownerId);
    }

    private Post post(UUID postId, UUID ownerId) {
        Post post = new Post();
        post.setId(postId);
        post.setUserId(ownerId);
        post.setImageUrl("https://example.test/image.jpg");
        post.setVisibilityMode(PostVisibilityMode.ALL_FRIENDS);
        return post;
    }

    private UserProfile profile(UUID userId, String username, String displayName) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUsername(username);
        profile.setDisplayName(displayName);
        return profile;
    }

    private Friendship friendship(UUID userId, UUID friendId) {
        Friendship friendship = new Friendship();
        friendship.setUserId(userId);
        friendship.setFriendId(friendId);
        return friendship;
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
