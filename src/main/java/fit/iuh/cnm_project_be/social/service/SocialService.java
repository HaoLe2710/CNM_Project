package fit.iuh.cnm_project_be.social.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.dto.UploadAttachmentResponse;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.enums.NotificationTargetType;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.social.dto.request.CreateMomentRequest;
import fit.iuh.cnm_project_be.social.dto.request.CreateMomentCommentRequest;
import fit.iuh.cnm_project_be.social.dto.request.CreatePostCommentRequest;
import fit.iuh.cnm_project_be.social.dto.request.CreatePostMediaItemRequest;
import fit.iuh.cnm_project_be.social.dto.request.CreatePostRequest;
import fit.iuh.cnm_project_be.social.dto.response.CommunityVideoFeedResponse;
import fit.iuh.cnm_project_be.social.dto.response.MomentCommentResponse;
import fit.iuh.cnm_project_be.social.dto.response.MomentResponse;
import fit.iuh.cnm_project_be.social.dto.response.PostAudienceResponse;
import fit.iuh.cnm_project_be.social.dto.response.PostCommentResponse;
import fit.iuh.cnm_project_be.social.dto.response.PostInteractionResponse;
import fit.iuh.cnm_project_be.social.dto.response.PostLikeResponse;
import fit.iuh.cnm_project_be.social.dto.response.PostMediaResponse;
import fit.iuh.cnm_project_be.social.dto.response.PostResponse;
import fit.iuh.cnm_project_be.social.dto.response.SocialMediaUploadResponse;
import fit.iuh.cnm_project_be.social.entity.Moment;
import fit.iuh.cnm_project_be.social.entity.MomentComment;
import fit.iuh.cnm_project_be.social.entity.PostComment;
import fit.iuh.cnm_project_be.social.entity.PostCommentLike;
import fit.iuh.cnm_project_be.social.entity.PostLike;
import fit.iuh.cnm_project_be.social.entity.Post;
import fit.iuh.cnm_project_be.social.entity.PostMedia;
import fit.iuh.cnm_project_be.social.entity.PostTag;
import fit.iuh.cnm_project_be.social.entity.PostVisibilityGrant;
import fit.iuh.cnm_project_be.social.entity.MomentReaction;
import fit.iuh.cnm_project_be.social.entity.MomentView;
import fit.iuh.cnm_project_be.social.enums.MediaType;
import fit.iuh.cnm_project_be.social.enums.MomentVisibilityMode;
import fit.iuh.cnm_project_be.social.enums.PostInteractionScope;
import fit.iuh.cnm_project_be.social.enums.PostVisibilityMode;
import fit.iuh.cnm_project_be.social.enums.ReactionType;
import fit.iuh.cnm_project_be.social.repository.MomentCommentRepository;
import fit.iuh.cnm_project_be.social.repository.MomentReactionRepository;
import fit.iuh.cnm_project_be.social.repository.MomentRepository;
import fit.iuh.cnm_project_be.social.repository.MomentViewRepository;
import fit.iuh.cnm_project_be.social.repository.PostCommentRepository;
import fit.iuh.cnm_project_be.social.repository.PostCommentLikeRepository;
import fit.iuh.cnm_project_be.social.repository.PostLikeRepository;
import fit.iuh.cnm_project_be.social.repository.PostMediaRepository;
import fit.iuh.cnm_project_be.social.repository.PostRepository;
import fit.iuh.cnm_project_be.social.repository.PostTagRepository;
import fit.iuh.cnm_project_be.social.repository.PostVisibilityGrantRepository;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import fit.iuh.cnm_project_be.user.dto.response.UserSummaryResponse;
import fit.iuh.cnm_project_be.user.entity.Friendship;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserBlockRepository;
import fit.iuh.cnm_project_be.user.repository.FriendshipRepository;
import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int VIDEO_CANDIDATE_MULTIPLIER = 3;
    private static final int STORY_WINDOW_HOURS = 24;
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}_.-]+)");

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostCommentLikeRepository postCommentLikeRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostTagRepository postTagRepository;
    private final PostVisibilityGrantRepository postVisibilityGrantRepository;
    private final MomentRepository momentRepository;
    private final MomentReactionRepository momentReactionRepository;
    private final MomentCommentRepository momentCommentRepository;
    private final MomentViewRepository momentViewRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserService userService;
    private final S3MediaStorageService s3MediaStorageService;
    private final NotificationDispatcher notificationDispatcher;

    @Transactional(readOnly = true)
    public List<PostResponse> getFriendPostFeed(Integer size) {
        UUID currentUserId = currentUser().getUserId();
        return postRepository.findByDeletedAtIsNullAndArchivedAtIsNullOrderByCreatedAtDesc(
                        PageRequest.of(0, normalizeFeedCandidateSize(size)))
                .stream()
                .filter(post -> canViewPost(post, currentUserId))
                .limit(normalizeSize(size))
                .map(post -> toPostResponse(post, currentUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PostResponse> getMyPosts(boolean archived, Integer size) {
        UUID currentUserId = currentUser().getUserId();
        List<Post> posts = archived
                ? postRepository.findByUserIdAndDeletedAtIsNullAndArchivedAtIsNotNullOrderByArchivedAtDesc(
                        currentUserId, PageRequest.of(0, normalizeSize(size)))
                : postRepository.findByUserIdAndDeletedAtIsNullAndArchivedAtIsNullOrderByCreatedAtDesc(
                        currentUserId, PageRequest.of(0, normalizeSize(size)));
        return posts.stream()
                .map(post -> toPostResponse(post, currentUserId))
                .toList();
    }

    @Transactional
    public PostResponse createPost(CreatePostRequest request) {
        UserProfile currentUser = currentUser();
        List<CreatePostMediaItemRequest> mediaItems = validatePostMediaItems(resolveRequestMediaItems(request));

        Post post = new Post();
        post.setUserId(currentUser.getUserId());
        post.setImageUrl(mediaItems.getFirst().getMediaUrl().trim());
        post.setCaption(trimToNull(request.getCaption()));
        post.setVisibilityMode(request.getVisibilityMode());

        Post savedPost = postRepository.save(post);
        savePostMedia(savedPost.getId(), mediaItems);
        syncPostAudience(savedPost, currentUser.getUserId(), request.getAllowedViewerIds(), request.getTaggedFriendIds());
        safeDispatchPostTagged(savedPost, currentUser.getUserId(), request.getTaggedFriendIds());
        return toPostResponse(savedPost, currentUser.getUserId());
    }

    @Transactional
    public PostResponse createPost(
            MultipartFile[] files,
            String caption,
            PostVisibilityMode visibilityMode,
            List<UUID> allowedViewerIds,
            List<UUID> taggedFriendIds) {
        UUID currentUserId = currentUser().getUserId();
        if (files == null || files.length == 0) {
            throw new BusinessException("Post must contain at least one image or video");
        }

        List<CreatePostMediaItemRequest> mediaItems = new ArrayList<>();
        for (MultipartFile file : files) {
            UploadAttachmentResponse upload = s3MediaStorageService.upload(currentUserId, file);
            CreatePostMediaItemRequest item = new CreatePostMediaItemRequest();
            item.setMediaUrl(upload.getUrl());
            item.setMediaType(toMediaType(upload.getType()));
            mediaItems.add(item);
        }
        mediaItems = validatePostMediaItems(mediaItems);

        Post post = new Post();
        post.setUserId(currentUserId);
        post.setImageUrl(mediaItems.getFirst().getMediaUrl());
        post.setCaption(trimToNull(caption));
        post.setVisibilityMode(visibilityMode == null ? PostVisibilityMode.ALL_FRIENDS : visibilityMode);

        Post savedPost = postRepository.save(post);
        savePostMedia(savedPost.getId(), mediaItems);
        syncPostAudience(savedPost, currentUserId, allowedViewerIds, taggedFriendIds);
        safeDispatchPostTagged(savedPost, currentUserId, taggedFriendIds);
        return toPostResponse(savedPost, currentUserId);
    }

    @Transactional
    public PostResponse archivePost(UUID postId) {
        Post post = getOwnedPost(postId);
        if (post.getArchivedAt() == null) {
            post.setArchivedAt(Instant.now());
        }
        return toPostResponse(postRepository.save(post), post.getUserId());
    }

    @Transactional
    public PostResponse restorePost(UUID postId) {
        Post post = getOwnedPost(postId);
        post.setArchivedAt(null);
        return toPostResponse(postRepository.save(post), post.getUserId());
    }

    @Transactional
    public void deletePost(UUID postId) {
        Post post = getOwnedPost(postId);
        postMediaRepository.deleteByPostId(postId);
        postTagRepository.deleteByPostId(postId);
        postVisibilityGrantRepository.deleteByPostId(postId);
        postRepository.delete(post);
    }

    @Transactional
    public PostResponse likePost(UUID postId) {
        UUID currentUserId = currentUser().getUserId();
        Post post = getInteractablePost(postId, currentUserId);

        if (!postLikeRepository.existsByPostIdAndUserId(postId, currentUserId)) {
            PostLike postLike = new PostLike();
            postLike.setPostId(postId);
            postLike.setUserId(currentUserId);
            PostLike savedLike = postLikeRepository.save(postLike);
            safeDispatchPostReaction(post, currentUserId, savedLike.getId(), false);
        }

        return toPostResponse(post, currentUserId);
    }

    @Transactional
    public PostResponse unlikePost(UUID postId) {
        UUID currentUserId = currentUser().getUserId();
        Post post = getInteractablePost(postId, currentUserId);
        postLikeRepository.deleteByPostIdAndUserId(postId, currentUserId);
        return toPostResponse(post, currentUserId);
    }

    @Transactional
    public PostCommentResponse addComment(UUID postId, CreatePostCommentRequest request) {
        UUID currentUserId = currentUser().getUserId();
        Post post = getInteractablePost(postId, currentUserId);

        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(currentUserId);
        comment.setContent(request.getContent().trim());

        PostComment savedComment = postCommentRepository.save(comment);
        Set<UUID> mentionedUserIds = resolveMentionedUserIds(savedComment.getContent(), post, currentUserId);
        safeDispatchCommentMentions(post, savedComment, currentUserId, mentionedUserIds);
        safeDispatchPostComment(post, savedComment, currentUserId, mentionedUserIds);
        return toPostCommentResponse(savedComment, currentUserId, List.of());
    }

    @Transactional
    public PostCommentResponse replyToComment(UUID parentCommentId, CreatePostCommentRequest request) {
        UUID currentUserId = currentUser().getUserId();
        PostComment parentComment = getVisibleComment(parentCommentId, currentUserId);
        getInteractablePost(parentComment.getPostId(), currentUserId);

        PostComment reply = new PostComment();
        reply.setPostId(parentComment.getPostId());
        reply.setUserId(currentUserId);
        reply.setParentCommentId(parentComment.getId());
        reply.setContent(request.getContent().trim());

        PostComment savedReply = postCommentRepository.save(reply);
        Post post = getVisiblePost(parentComment.getPostId(), currentUserId);
        Set<UUID> mentionedUserIds = resolveMentionedUserIds(savedReply.getContent(), post, currentUserId);
        safeDispatchCommentMentions(post, savedReply, currentUserId, mentionedUserIds);
        safeDispatchCommentReply(post, parentComment, savedReply, currentUserId, mentionedUserIds);
        safeDispatchPostComment(post, savedReply, currentUserId, union(mentionedUserIds, parentComment.getUserId()));
        return toPostCommentResponse(savedReply, currentUserId, List.of());
    }

    @Transactional
    public PostCommentResponse likeComment(UUID commentId) {
        UUID currentUserId = currentUser().getUserId();
        PostComment comment = getVisibleComment(commentId, currentUserId);
        getInteractablePost(comment.getPostId(), currentUserId);

        if (!postCommentLikeRepository.existsByCommentIdAndUserId(commentId, currentUserId)) {
            PostCommentLike like = new PostCommentLike();
            like.setCommentId(commentId);
            like.setUserId(currentUserId);
            postCommentLikeRepository.save(like);
        }

        return toPostCommentResponse(comment, currentUserId, List.of());
    }

    @Transactional
    public PostCommentResponse unlikeComment(UUID commentId) {
        UUID currentUserId = currentUser().getUserId();
        PostComment comment = getVisibleComment(commentId, currentUserId);
        getInteractablePost(comment.getPostId(), currentUserId);
        postCommentLikeRepository.deleteByCommentIdAndUserId(commentId, currentUserId);
        return toPostCommentResponse(comment, currentUserId, List.of());
    }

    @Transactional(readOnly = true)
    public List<PostCommentResponse> getPostComments(UUID postId) {
        UUID currentUserId = currentUser().getUserId();
        Post post = getVisiblePost(postId, currentUserId);
        PostInteractionScope scope = resolveInteractionScope(post, currentUserId);
        Set<UUID> visibleUserIds = resolveVisibleUserIds(post, currentUserId, scope);

        List<PostComment> allComments = postCommentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(postId);
        List<PostComment> visibleComments = allComments.stream()
                .filter(comment -> canSeeActor(comment.getUserId(), scope, visibleUserIds))
                .toList();

        return buildCommentTree(visibleComments, null, currentUserId);
    }

    @Transactional(readOnly = true)
    public PostInteractionResponse getPostInteractions(UUID postId) {
        UUID currentUserId = currentUser().getUserId();
        Post post = getVisiblePost(postId, currentUserId);
        PostInteractionScope scope = resolveInteractionScope(post, currentUserId);

        List<PostLike> allLikes = postLikeRepository.findByPostIdOrderByCreatedAtDesc(postId);
        List<PostComment> allComments = postCommentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(postId);

        Set<UUID> visibleUserIds = resolveVisibleUserIds(post, currentUserId, scope);

        List<PostLikeResponse> visibleLikes = allLikes.stream()
                .filter(like -> canSeeActor(like.getUserId(), scope, visibleUserIds))
                .map(this::toPostLikeResponse)
                .toList();

        List<PostCommentResponse> visibleComments = allComments.stream()
                .filter(comment -> canSeeActor(comment.getUserId(), scope, visibleUserIds))
                .map(comment -> toPostCommentResponse(comment, currentUserId, List.of()))
                .toList();

        return PostInteractionResponse.builder()
                .scope(scope)
                .totalLikeCount(allLikes.size())
                .totalCommentCount(allComments.size())
                .visibleLikeCount(visibleLikes.size())
                .visibleCommentCount(visibleComments.size())
                .likes(visibleLikes)
                .comments(visibleComments)
                .build();
    }

    @Transactional
    public MomentResponse createMoment(CreateMomentRequest request) {
        UserProfile currentUser = currentUser();

        Moment moment = new Moment();
        moment.setUserId(currentUser.getUserId());
        moment.setMediaUrl(request.getMediaUrl().trim());
        moment.setMediaType(request.getMediaType());
        moment.setCaption(trimToNull(request.getCaption()));
        moment.setCoverUrl(trimToNull(request.getCoverUrl()));
        moment.setDurationSeconds(request.getDurationSeconds() == null ? 0 : Math.max(request.getDurationSeconds(), 0));
        moment.setVisibilityMode(request.getVisibilityMode() == null ? MomentVisibilityMode.FRIENDS : request.getVisibilityMode());

        return toMomentResponse(momentRepository.save(moment));
    }

    @Transactional(readOnly = true)
    public List<MomentResponse> getMyMoments(Integer size) {
        UUID currentUserId = currentUser().getUserId();
        return momentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUserId).stream()
                .limit(normalizeSize(size))
                .map(this::toMomentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MomentResponse> getStoryFeed(Integer size) {
        UUID currentUserId = currentUser().getUserId();
        Set<UUID> visibleUserIds = new HashSet<>(getFriendIds(currentUserId));
        visibleUserIds.add(currentUserId);

        if (visibleUserIds.isEmpty()) {
            return List.of();
        }

        Instant fromTime = Instant.now().minusSeconds(STORY_WINDOW_HOURS * 3600L);
        return momentRepository.findStoryFeedByUserIds(
                        visibleUserIds,
                        fromTime,
                        PageRequest.of(0, normalizeFeedCandidateSize(size)))
                .stream()
                .filter(moment -> canViewMoment(moment, currentUserId))
                .limit(normalizeSize(size))
                .map(this::toMomentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityVideoFeedResponse getCommunityVideoFeed(String cursor, Integer size) {
        int limit = normalizeSize(size);
        CursorData cursorData = decodeCursor(cursor);
        List<Moment> candidates = momentRepository.findPublicCommunityVideos(
                cursorData.createdAt,
                cursorData.momentId,
                PageRequest.of(0, Math.min(limit * 3, MAX_PAGE_SIZE))
        );

        UUID currentUserId = currentUser().getUserId();
        List<Moment> ranked = candidates.stream()
                .filter(moment -> !isBlockedEitherWay(currentUserId, moment.getUserId()))
                .sorted((left, right) -> Double.compare(scoreMoment(right), scoreMoment(left)))
                .limit(limit)
                .toList();

        String nextCursor = ranked.isEmpty() ? null : encodeCursor(ranked.getLast());
        boolean hasMore = candidates.size() > ranked.size();
        return CommunityVideoFeedResponse.builder()
                .items(ranked.stream().map(this::toMomentResponse).toList())
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Transactional
    public void deleteMoment(UUID momentId) {
        Moment moment = getOwnedMoment(momentId);
        momentRepository.delete(moment);
    }

    @Transactional(readOnly = true)
    public List<MomentResponse> getRandomVideoFeed(Integer size) {
        UUID currentUserId = currentUser().getUserId();
        int limit = normalizeSize(size);
        List<UUID> friendIds = getFriendIds(currentUserId);

        List<Moment> selected = new ArrayList<>();
        Set<UUID> selectedIds = new HashSet<>();

        if (!friendIds.isEmpty()) {
            List<Moment> friendVideos = new ArrayList<>(momentRepository.findByUserIdsAndMediaType(
                    friendIds,
                    MediaType.VIDEO,
                    PageRequest.of(0, Math.min(limit * VIDEO_CANDIDATE_MULTIPLIER, MAX_PAGE_SIZE))
            ));
            Collections.shuffle(friendVideos);
            for (Moment moment : friendVideos) {
                if (selected.size() >= limit) {
                    break;
                }
                if (selectedIds.add(moment.getId())) {
                    selected.add(moment);
                }
            }
        }

        if (selected.size() < limit) {
            int remaining = limit - selected.size();
            List<Moment> fallbackVideos = selectedIds.isEmpty()
                    ? new ArrayList<>(momentRepository.findByMediaType(MediaType.VIDEO, PageRequest.of(0, MAX_PAGE_SIZE)))
                    : new ArrayList<>(momentRepository.findByMediaTypeExcludingIds(
                            MediaType.VIDEO,
                            selectedIds,
                            PageRequest.of(0, MAX_PAGE_SIZE)
                    ));
            Collections.shuffle(fallbackVideos);
            for (Moment moment : fallbackVideos) {
                if (remaining == 0) {
                    break;
                }
                if (selectedIds.add(moment.getId())) {
                    selected.add(moment);
                    remaining--;
                }
            }
        }

        return selected.stream()
                .map(this::toMomentResponse)
                .toList();
    }

    @Transactional
    public MomentResponse likeVideo(UUID momentId) {
        UUID currentUserId = currentUser().getUserId();
        Moment moment = getInteractableMoment(momentId, currentUserId);
        if (!momentReactionRepository.existsByMomentIdAndUserId(momentId, currentUserId)) {
            MomentReaction reaction = new MomentReaction();
            reaction.setMomentId(momentId);
            reaction.setUserId(currentUserId);
            reaction.setReactionType(ReactionType.LIKE);
            MomentReaction savedReaction = momentReactionRepository.save(reaction);
            safeDispatchPostReaction(moment, currentUserId, savedReaction.getId(), true);
        }
        return toMomentResponse(moment);
    }

    @Transactional
    public MomentResponse unlikeVideo(UUID momentId) {
        UUID currentUserId = currentUser().getUserId();
        Moment moment = getInteractableMoment(momentId, currentUserId);
        momentReactionRepository.deleteByMomentIdAndUserId(momentId, currentUserId);
        return toMomentResponse(moment);
    }

    @Transactional(readOnly = true)
    public List<MomentCommentResponse> getMomentComments(UUID momentId) {
        UUID currentUserId = currentUser().getUserId();
        Moment moment = getVisibleMoment(momentId, currentUserId);
        return momentCommentRepository.findByMomentIdAndDeletedAtIsNullOrderByCreatedAtAsc(moment.getId()).stream()
                .map(this::toMomentCommentResponse)
                .toList();
    }

    @Transactional
    public MomentCommentResponse addMomentComment(UUID momentId, CreateMomentCommentRequest request) {
        UUID currentUserId = currentUser().getUserId();
        Moment moment = getInteractableMoment(momentId, currentUserId);
        MomentComment comment = new MomentComment();
        comment.setId(UUID.randomUUID());
        comment.setMomentId(moment.getId());
        comment.setUserId(currentUserId);
        comment.setContent(request.getContent().trim());
        MomentComment savedComment = momentCommentRepository.save(comment);
        safeDispatchMomentComment(moment, savedComment, currentUserId);
        return toMomentCommentResponse(savedComment);
    }

    @Transactional
    public void recordMomentView(UUID momentId) {
        UUID currentUserId = currentUser().getUserId();
        Moment moment = getVisibleMoment(momentId, currentUserId);
        if (!momentViewRepository.existsByMomentIdAndViewerId(momentId, currentUserId)) {
            MomentView view = new MomentView();
            view.setMomentId(momentId);
            view.setViewerId(currentUserId);
            momentViewRepository.save(view);
        }
    }

    @Transactional
    public SocialMediaUploadResponse uploadMedia(MultipartFile file) {
        UUID currentUserId = currentUser().getUserId();
        UploadAttachmentResponse upload = s3MediaStorageService.upload(currentUserId, file);

        MediaType mediaType = toMediaType(upload.getType());
        return SocialMediaUploadResponse.builder()
                .url(upload.getUrl())
                .storageKey(upload.getStorageKey())
                .fileName(upload.getFileName())
                .contentType(upload.getContentType())
                .fileSize(upload.getFileSize())
                .mediaType(mediaType)
                .build();
    }

    private Post getOwnedPost(UUID postId) {
        UUID currentUserId = currentUser().getUserId();
        Post post = getVisiblePost(postId, currentUserId);

        if (!post.getUserId().equals(currentUserId)) {
            throw new BusinessException("You do not have permission to modify this post");
        }
        return post;
    }

    private Moment getOwnedMoment(UUID momentId) {
        UUID currentUserId = currentUser().getUserId();
        Moment moment = momentRepository.findById(momentId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Moment not found"));

        if (!moment.getUserId().equals(currentUserId)) {
            throw new BusinessException("You do not have permission to modify this moment");
        }
        return moment;
    }

    private Moment getVisibleMoment(UUID momentId, UUID currentUserId) {
        Moment moment = momentRepository.findById(momentId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Moment not found"));
        if (!canViewMoment(moment, currentUserId)) {
            throw new NotFoundException("Moment not found");
        }
        return moment;
    }

    private Moment getInteractableMoment(UUID momentId, UUID currentUserId) {
        Moment moment = getVisibleMoment(momentId, currentUserId);
        if (moment.getUserId().equals(currentUserId)) {
            return moment;
        }
        if (moment.getVisibilityMode() == MomentVisibilityMode.PUBLIC) {
            return moment;
        }
        if (!areFriends(currentUserId, moment.getUserId())) {
            throw new BusinessException("You cannot interact with this video");
        }
        return moment;
    }

    private Post getInteractablePost(UUID postId, UUID currentUserId) {
        Post post = getVisiblePost(postId, currentUserId);
        if (!post.getUserId().equals(currentUserId) && !areFriends(currentUserId, post.getUserId())) {
            throw new BusinessException("You cannot interact with this post");
        }
        return post;
    }

    private Post getVisiblePost(UUID postId, UUID currentUserId) {
        Post post = postRepository.findById(postId)
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> item.getArchivedAt() == null || item.getUserId().equals(currentUserId))
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (!canViewPost(post, currentUserId) && !post.getUserId().equals(currentUserId)) {
            throw new NotFoundException("Post not found");
        }
        return post;
    }

    private PostComment getVisibleComment(UUID commentId, UUID currentUserId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        Post post = getVisiblePost(comment.getPostId(), currentUserId);
        PostInteractionScope scope = resolveInteractionScope(post, currentUserId);
        Set<UUID> visibleUserIds = resolveVisibleUserIds(post, currentUserId, scope);
        if (!canSeeActor(comment.getUserId(), scope, visibleUserIds) && !comment.getUserId().equals(currentUserId)) {
            throw new NotFoundException("Comment not found");
        }
        return comment;
    }

    private PostResponse toPostResponse(Post post, UUID currentUserId) {
        UserProfile author = userService.getUser(post.getUserId());
        PostInteractionScope interactionScope = resolveInteractionScope(post, currentUserId);
        List<PostMediaResponse> mediaItems = getPostMedia(post.getId());
        String previewUrl = !mediaItems.isEmpty() ? mediaItems.getFirst().getMediaUrl() : post.getImageUrl();

        return PostResponse.builder()
                .id(post.getId())
                .author(toUserSummary(author))
                .imageUrl(previewUrl)
                .mediaItems(mediaItems)
                .caption(post.getCaption())
                .archived(post.getArchivedAt() != null)
                .archivedAt(post.getArchivedAt())
                .visibilityMode(post.getVisibilityMode())
                .taggedFriends(getTaggedUsers(post.getId()))
                .likeCount(postLikeRepository.countByPostId(post.getId()))
                .commentCount(postCommentRepository.countByPostIdAndDeletedAtIsNull(post.getId()))
                .likedByCurrentUser(postLikeRepository.existsByPostIdAndUserId(post.getId(), currentUserId))
                .interactionScope(interactionScope)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private List<PostMediaResponse> getPostMedia(UUID postId) {
        List<PostMedia> medias = postMediaRepository.findByPostIdOrderBySortOrderAscCreatedAtAsc(postId);
        if (medias.isEmpty()) {
            return List.of();
        }
        return medias.stream()
                .map(this::toPostMediaResponse)
                .toList();
    }

    private PostMediaResponse toPostMediaResponse(PostMedia postMedia) {
        return PostMediaResponse.builder()
                .id(postMedia.getId())
                .mediaUrl(postMedia.getMediaUrl())
                .mediaType(postMedia.getMediaType())
                .sortOrder(postMedia.getSortOrder())
                .build();
    }

    private PostLikeResponse toPostLikeResponse(PostLike postLike) {
        UserProfile user = userService.getUser(postLike.getUserId());
        return PostLikeResponse.builder()
                .id(postLike.getId())
                .user(toUserSummary(user))
                .createdAt(postLike.getCreatedAt())
                .build();
    }

    private PostCommentResponse toPostCommentResponse(
            PostComment postComment,
            UUID currentUserId,
            List<PostCommentResponse> replies) {
        UserProfile user = userService.getUser(postComment.getUserId());
        return PostCommentResponse.builder()
                .id(postComment.getId())
                .parentCommentId(postComment.getParentCommentId())
                .user(toUserSummary(user))
                .content(postComment.getContent())
                .likeCount(postCommentLikeRepository.countByCommentId(postComment.getId()))
                .likedByCurrentUser(postCommentLikeRepository.existsByCommentIdAndUserId(postComment.getId(), currentUserId))
                .createdAt(postComment.getCreatedAt())
                .updatedAt(postComment.getUpdatedAt())
                .replies(replies)
                .build();
    }

    private MomentResponse toMomentResponse(Moment moment) {
        UserProfile author = userService.getUser(moment.getUserId());
        UUID currentUserId = currentUser().getUserId();
        return MomentResponse.builder()
                .id(moment.getId())
                .author(toUserSummary(author))
                .mediaUrl(moment.getMediaUrl())
                .coverUrl(moment.getCoverUrl())
                .mediaType(moment.getMediaType())
                .caption(moment.getCaption())
                .durationSeconds(moment.getDurationSeconds())
                .visibilityMode(moment.getVisibilityMode())
                .likeCount(momentReactionRepository.countByMomentId(moment.getId()))
                .commentCount(momentCommentRepository.countByMomentIdAndDeletedAtIsNull(moment.getId()))
                .shareCount(moment.getShareCount() == null ? 0L : moment.getShareCount())
                .viewCount(momentViewRepository.countByMomentId(moment.getId()))
                .likedByCurrentUser(momentReactionRepository.existsByMomentIdAndUserId(moment.getId(), currentUserId))
                .followedByCurrentUser(areFriends(currentUserId, moment.getUserId()))
                .createdAt(moment.getCreatedAt())
                .build();
    }

    private MomentCommentResponse toMomentCommentResponse(MomentComment comment) {
        return MomentCommentResponse.builder()
                .id(comment.getId())
                .user(toUserSummary(userService.getUser(comment.getUserId())))
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private UserSummaryResponse toUserSummary(UserProfile userProfile) {
        return UserSummaryResponse.builder()
                .userId(userProfile.getUserId())
                .username(userProfile.getUsername())
                .displayName(userProfile.getDisplayName())
                .avatarUrl(userProfile.getAvatarUrl())
                .build();
    }

    private List<UUID> getFriendIds(UUID currentUserId) {
        return friendshipRepository.findByUserIdAndDeletedAtIsNull(currentUserId).stream()
                .map(Friendship::getFriendId)
                .filter(friendId -> !isBlockedEitherWay(currentUserId, friendId))
                .toList();
    }

    private void syncPostAudience(Post post, UUID currentUserId, List<UUID> allowedViewerIds, List<UUID> taggedFriendIds) {
        validateVisibilityRequest(post.getVisibilityMode(), allowedViewerIds);

        Set<UUID> friendIds = new HashSet<>(getFriendIds(currentUserId));
        Set<UUID> allowedSet = sanitizeFriendSelection(allowedViewerIds, friendIds, "allowed viewers");
        Set<UUID> taggedSet = sanitizeFriendSelection(taggedFriendIds, friendIds, "tagged friends");
        allowedSet = new HashSet<>(allowedSet);
        allowedSet.addAll(taggedSet);

        if (post.getVisibilityMode() == PostVisibilityMode.SELECTED_FRIENDS && allowedSet.isEmpty()) {
            throw new BusinessException("Selected-friends visibility requires at least one friend");
        }

        postVisibilityGrantRepository.deleteByPostId(post.getId());
        postTagRepository.deleteByPostId(post.getId());

        allowedSet.forEach(viewerId -> {
            PostVisibilityGrant grant = new PostVisibilityGrant();
            grant.setPostId(post.getId());
            grant.setViewerUserId(viewerId);
            postVisibilityGrantRepository.save(grant);
        });

        taggedSet.forEach(taggedUserId -> {
            PostTag tag = new PostTag();
            tag.setPostId(post.getId());
            tag.setTaggedUserId(taggedUserId);
            postTagRepository.save(tag);
        });
    }

    private void validateVisibilityRequest(PostVisibilityMode visibilityMode, List<UUID> allowedViewerIds) {
        if (visibilityMode == null) {
            throw new BusinessException("Visibility mode is required");
        }
        if (visibilityMode == PostVisibilityMode.ALL_FRIENDS && allowedViewerIds != null && !allowedViewerIds.isEmpty()) {
            throw new BusinessException("Allowed viewers are only supported for selected-friends visibility");
        }
    }

    private Set<UUID> sanitizeFriendSelection(List<UUID> userIds, Set<UUID> friendIds, String fieldName) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> sanitized = new HashSet<>(userIds);
        if (!friendIds.containsAll(sanitized)) {
            throw new BusinessException("All " + fieldName + " must be current friends");
        }
        return sanitized;
    }

    private List<UserSummaryResponse> getTaggedUsers(UUID postId) {
        return postTagRepository.findByPostId(postId).stream()
                .map(PostTag::getTaggedUserId)
                .map(userService::getUser)
                .map(this::toUserSummary)
                .toList();
    }

    private List<UserSummaryResponse> getAllowedViewers(UUID postId) {
        return postVisibilityGrantRepository.findByPostId(postId).stream()
                .map(PostVisibilityGrant::getViewerUserId)
                .map(userService::getUser)
                .map(this::toUserSummary)
                .toList();
    }

    private void safeDispatchPostTagged(Post post, UUID actorId, List<UUID> taggedFriendIds) {
        if (notificationDispatcher == null || post == null || taggedFriendIds == null || taggedFriendIds.isEmpty()) {
            return;
        }
        List<UUID> recipients = taggedFriendIds.stream()
                .filter(userId -> userId != null && !userId.equals(actorId))
                .distinct()
                .filter(userId -> canViewPost(post, userId))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        safeDispatchSocial(NotificationType.POST_TAGGED,
                NotificationTargetType.POST,
                actorId,
                recipients,
                post.getId(),
                null,
                "post:" + post.getId() + ":type:TAGGED",
                Map.of("postId", post.getId().toString()),
                true);
    }

    private void safeDispatchPostReaction(Post post, UUID actorId, UUID reactionId, boolean moment) {
        if (post == null) {
            return;
        }
        safeDispatchPostReaction(post.getId(), post.getUserId(), actorId, reactionId, moment);
    }

    private void safeDispatchPostReaction(Moment moment, UUID actorId, Long reactionId, boolean momentEvent) {
        if (moment == null) {
            return;
        }
        safeDispatchPostReaction(moment.getId(), moment.getUserId(), actorId, reactionId, momentEvent);
    }

    private void safeDispatchPostReaction(UUID targetPostId, UUID ownerId, UUID actorId, Object reactionId, boolean moment) {
        if (ownerId == null || ownerId.equals(actorId)) {
            return;
        }
        safeDispatchSocial(NotificationType.POST_REACTION,
                NotificationTargetType.POST,
                actorId,
                List.of(ownerId),
                targetPostId,
                null,
                "reaction:" + reactionId,
                Map.of(
                        "postId", targetPostId.toString(),
                        "objectType", moment ? "MOMENT" : "POST",
                        "reactionId", String.valueOf(reactionId)
                ),
                true);
    }

    private void safeDispatchPostComment(
            Post post,
            PostComment comment,
            UUID actorId,
            Set<UUID> alreadyNotified) {
        if (post == null || comment == null || post.getUserId().equals(actorId) || alreadyNotified.contains(post.getUserId())) {
            return;
        }
        safeDispatchSocial(NotificationType.POST_COMMENT,
                NotificationTargetType.COMMENT,
                actorId,
                List.of(post.getUserId()),
                post.getId(),
                comment.getId(),
                "comment:" + comment.getId() + ":type:POST_COMMENT",
                Map.of(
                        "postId", post.getId().toString(),
                        "commentId", comment.getId().toString(),
                        "commentPreview", buildCommentPreview(comment.getContent())
                ),
                true);
    }

    private void safeDispatchMomentComment(Moment moment, MomentComment comment, UUID actorId) {
        if (moment == null || comment == null || moment.getUserId().equals(actorId)) {
            return;
        }
        safeDispatchSocial(NotificationType.POST_COMMENT,
                NotificationTargetType.COMMENT,
                actorId,
                List.of(moment.getUserId()),
                moment.getId(),
                comment.getId(),
                "comment:" + comment.getId() + ":type:MOMENT_COMMENT",
                Map.of(
                        "postId", moment.getId().toString(),
                        "commentId", comment.getId().toString(),
                        "objectType", "MOMENT",
                        "commentPreview", buildCommentPreview(comment.getContent())
                ),
                true);
    }

    private void safeDispatchCommentReply(
            Post post,
            PostComment parentComment,
            PostComment reply,
            UUID actorId,
            Set<UUID> alreadyNotified) {
        if (post == null || parentComment == null || reply == null
                || parentComment.getUserId().equals(actorId)
                || alreadyNotified.contains(parentComment.getUserId())) {
            return;
        }
        safeDispatchSocial(NotificationType.COMMENT_REPLY,
                NotificationTargetType.COMMENT,
                actorId,
                List.of(parentComment.getUserId()),
                post.getId(),
                reply.getId(),
                "comment:" + reply.getId() + ":reply-recipient",
                Map.of(
                        "postId", post.getId().toString(),
                        "commentId", reply.getId().toString(),
                        "parentCommentId", parentComment.getId().toString(),
                        "commentPreview", buildCommentPreview(reply.getContent())
                ),
                true);
    }

    private void safeDispatchCommentMentions(
            Post post,
            PostComment comment,
            UUID actorId,
            Set<UUID> mentionedUserIds) {
        if (post == null || comment == null || mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return;
        }
        List<UUID> recipients = mentionedUserIds.stream()
                .filter(userId -> userId != null && !userId.equals(actorId))
                .distinct()
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        safeDispatchSocial(NotificationType.COMMENT_MENTION,
                NotificationTargetType.COMMENT,
                actorId,
                recipients,
                post.getId(),
                comment.getId(),
                "comment:" + comment.getId() + ":mention",
                Map.of(
                        "postId", post.getId().toString(),
                        "commentId", comment.getId().toString(),
                        "commentPreview", buildCommentPreview(comment.getContent())
                ),
                true);
    }

    private void safeDispatchSocial(
            NotificationType type,
            NotificationTargetType targetType,
            UUID actorId,
            List<UUID> recipients,
            UUID postId,
            UUID commentId,
            String dedupKeyPrefix,
            Map<String, Object> metadata,
            boolean recipientDirectlyAffected) {
        if (notificationDispatcher == null || recipients == null || recipients.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            if (metadata != null) {
                payload.putAll(metadata);
            }
            payload.put("actorName", displayName(actorId));
            notificationDispatcher.dispatch(NotificationDispatchRequest.builder()
                    .type(type)
                    .targetType(targetType)
                    .targetId(commentId != null ? commentId : postId)
                    .actorId(actorId)
                    .explicitRecipientIds(recipients)
                    .postId(postId)
                    .commentId(commentId)
                    .metadata(payload)
                    .dedupKeyPrefix(dedupKeyPrefix)
                    .directMention(type == NotificationType.COMMENT_MENTION)
                    .replyToRecipientMessage(type == NotificationType.COMMENT_REPLY)
                    .recipientDirectlyAffected(recipientDirectlyAffected)
                    .build());
        } catch (Exception ex) {
            log.warn("[SocialService] Notification dispatch failed type={} actor={} target={} comment={}: {}",
                    type,
                    actorId,
                    postId,
                    commentId,
                    ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PostAudienceResponse getPostAudience(UUID postId) {
        Post post = getOwnedPost(postId);
        return PostAudienceResponse.builder()
                .visibilityMode(post.getVisibilityMode())
                .taggedFriends(getTaggedUsers(post.getId()))
                .allowedViewers(getAllowedViewers(post.getId()))
                .build();
    }

    private boolean areFriends(UUID currentUserId, UUID otherUserId) {
        if (isBlockedEitherWay(currentUserId, otherUserId)) {
            return false;
        }
        return friendshipRepository.existsByUserIdAndFriendIdAndDeletedAtIsNull(currentUserId, otherUserId)
                || friendshipRepository.existsByUserIdAndFriendIdAndDeletedAtIsNull(otherUserId, currentUserId);
    }

    private PostInteractionScope resolveInteractionScope(Post post, UUID currentUserId) {
        if (!canViewPost(post, currentUserId)) {
            return PostInteractionScope.NONE;
        }
        if (post.getUserId().equals(currentUserId)) {
            return PostInteractionScope.ALL;
        }
        if (areFriends(currentUserId, post.getUserId())) {
            return PostInteractionScope.FRIENDS_ONLY;
        }
        return PostInteractionScope.NONE;
    }

    private Set<UUID> resolveVisibleUserIds(Post post, UUID currentUserId, PostInteractionScope scope) {
        if (scope == PostInteractionScope.ALL) {
            return Set.of();
        }
        if (scope == PostInteractionScope.NONE) {
            return Set.of();
        }

        Set<UUID> visibleIds = new HashSet<>(getFriendIds(currentUserId));
        visibleIds.add(currentUserId);
        visibleIds.add(post.getUserId());
        return visibleIds;
    }

    private boolean canSeeActor(UUID actorUserId, PostInteractionScope scope, Set<UUID> visibleUserIds) {
        if (scope == PostInteractionScope.ALL) {
            return true;
        }
        if (scope == PostInteractionScope.NONE) {
            return false;
        }
        return visibleUserIds.contains(actorUserId);
    }

    private List<PostCommentResponse> buildCommentTree(
            List<PostComment> comments,
            UUID parentCommentId,
            UUID currentUserId) {
        return comments.stream()
                .filter(comment -> {
                    if (parentCommentId == null) {
                        return comment.getParentCommentId() == null;
                    }
                    return parentCommentId.equals(comment.getParentCommentId());
                })
                .map(comment -> toPostCommentResponse(
                        comment,
                        currentUserId,
                        buildCommentTree(comments, comment.getId(), currentUserId)
                ))
                .toList();
    }

    private UserProfile currentUser() {
        return userService.getMyProfile();
    }

    private Set<UUID> resolveMentionedUserIds(String content, Post post, UUID actorId) {
        if (content == null || content.isBlank() || post == null) {
            return Set.of();
        }
        Set<String> mentionedUsernames = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String username = matcher.group(1);
            if (username != null && !username.isBlank()) {
                mentionedUsernames.add(username.toLowerCase());
            }
        }
        if (mentionedUsernames.isEmpty()) {
            return Set.of();
        }

        Set<UUID> candidateIds = new LinkedHashSet<>(getFriendIds(actorId));
        candidateIds.add(post.getUserId());
        candidateIds.addAll(postTagRepository.findByPostId(post.getId()).stream()
                .map(PostTag::getTaggedUserId)
                .toList());

        Set<UUID> recipients = new LinkedHashSet<>();
        for (UUID candidateId : candidateIds) {
            if (candidateId == null || candidateId.equals(actorId) || !canViewPost(post, candidateId)) {
                continue;
            }
            try {
                UserProfile profile = userService.getUser(candidateId);
                String username = profile.getUsername();
                if (username != null && mentionedUsernames.contains(username.toLowerCase())) {
                    recipients.add(candidateId);
                }
            } catch (Exception ignored) {
                // Ignore stale candidate ids.
            }
        }
        return recipients;
    }

    private Set<UUID> union(Set<UUID> base, UUID extra) {
        Set<UUID> result = new LinkedHashSet<>();
        if (base != null) {
            result.addAll(base);
        }
        if (extra != null) {
            result.add(extra);
        }
        return result;
    }

    private String displayName(UUID userId) {
        if (userId == null) {
            return "Ai đó";
        }
        try {
            UserProfile profile = userService.getUser(userId);
            if (profile.getDisplayName() != null && !profile.getDisplayName().isBlank()) {
                return profile.getDisplayName();
            }
            if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
                return profile.getUsername();
            }
        } catch (Exception ignored) {
            // Fallback below.
        }
        return "Ai đó";
    }

    private String buildCommentPreview(String content) {
        if (content == null || content.isBlank()) {
            return "Đã gửi một bình luận";
        }
        String trimmed = content.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 97) + "...";
    }

    private boolean canViewPost(Post post, UUID viewerUserId) {
        if (post.getUserId().equals(viewerUserId)) {
            return true;
        }
        if (isBlockedEitherWay(post.getUserId(), viewerUserId)) {
            return false;
        }
        if (!areFriends(viewerUserId, post.getUserId())) {
            return false;
        }
        if (post.getVisibilityMode() == PostVisibilityMode.ALL_FRIENDS) {
            return true;
        }
        return postVisibilityGrantRepository.existsByPostIdAndViewerUserId(post.getId(), viewerUserId);
    }

    private boolean canViewMoment(Moment moment, UUID viewerUserId) {
        if (moment.getUserId().equals(viewerUserId)) {
            return true;
        }
        if (isBlockedEitherWay(moment.getUserId(), viewerUserId)) {
            return false;
        }
        return switch (moment.getVisibilityMode()) {
            case PUBLIC -> true;
            case FRIENDS -> areFriends(viewerUserId, moment.getUserId());
            case PRIVATE -> false;
        };
    }

    private List<CreatePostMediaItemRequest> validatePostMediaItems(List<CreatePostMediaItemRequest> mediaItems) {
        if (mediaItems == null || mediaItems.isEmpty()) {
            throw new BusinessException("Post must contain at least one image or video");
        }
        if (mediaItems.size() > 10) {
            throw new BusinessException("Post can contain at most 10 media items");
        }
        for (CreatePostMediaItemRequest mediaItem : mediaItems) {
            if (mediaItem == null || mediaItem.getMediaUrl() == null || mediaItem.getMediaUrl().isBlank()) {
                throw new BusinessException("Each media item must include a URL");
            }
            if (mediaItem.getMediaType() == null) {
                throw new BusinessException("Each media item must include a media type");
            }
        }
        return mediaItems;
    }

    private List<CreatePostMediaItemRequest> resolveRequestMediaItems(CreatePostRequest request) {
        if (request.getMediaItems() != null && !request.getMediaItems().isEmpty()) {
            return request.getMediaItems();
        }
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            CreatePostMediaItemRequest fallback = new CreatePostMediaItemRequest();
            fallback.setMediaUrl(request.getImageUrl().trim());
            fallback.setMediaType(MediaType.IMAGE);
            return List.of(fallback);
        }
        return List.of();
    }

    private void savePostMedia(UUID postId, List<CreatePostMediaItemRequest> mediaItems) {
        postMediaRepository.deleteByPostId(postId);
        for (int index = 0; index < mediaItems.size(); index++) {
            CreatePostMediaItemRequest item = mediaItems.get(index);
            PostMedia postMedia = new PostMedia();
            postMedia.setPostId(postId);
            postMedia.setMediaUrl(item.getMediaUrl().trim());
            postMedia.setMediaType(item.getMediaType());
            postMedia.setSortOrder(index);
            postMediaRepository.save(postMedia);
        }
    }

    private boolean isBlockedEitherWay(UUID userA, UUID userB) {
        return userBlockRepository.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(userA, userB)
                || userBlockRepository.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(userB, userA);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int normalizeFeedCandidateSize(Integer size) {
        return Math.min(normalizeSize(size) * 3, MAX_PAGE_SIZE);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MediaType toMediaType(MessageType messageType) {
        if (messageType == MessageType.IMAGE) {
            return MediaType.IMAGE;
        }
        if (messageType == MessageType.VIDEO) {
            return MediaType.VIDEO;
        }
        throw new BusinessException("Only image and video files are supported");
    }

    private String encodeCursor(Moment moment) {
        String raw = moment.getCreatedAt().toEpochMilli() + "|" + moment.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private CursorData decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new CursorData(null, null);
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            return new CursorData(Instant.ofEpochMilli(Long.parseLong(parts[0])), UUID.fromString(parts[1]));
        } catch (Exception ex) {
            throw new BusinessException("Invalid community feed cursor");
        }
    }

    private double scoreMoment(Moment moment) {
        long likes = momentReactionRepository.countByMomentId(moment.getId());
        long comments = momentCommentRepository.countByMomentIdAndDeletedAtIsNull(moment.getId());
        long views = momentViewRepository.countByMomentId(moment.getId());
        long shares = moment.getShareCount() == null ? 0L : moment.getShareCount();
        long ageHours = Math.max(1L, (Instant.now().toEpochMilli() - moment.getCreatedAt().toEpochMilli()) / (1000L * 60L * 60L));
        return (likes * 3.0) + (comments * 4.0) + (views * 1.0) + (shares * 5.0) + (48.0 / ageHours);
    }

    private record CursorData(Instant createdAt, UUID momentId) {
    }
}
