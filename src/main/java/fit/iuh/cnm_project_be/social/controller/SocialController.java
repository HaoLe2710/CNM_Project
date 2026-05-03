package fit.iuh.cnm_project_be.social.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.social.dto.request.CreateMomentRequest;
import fit.iuh.cnm_project_be.social.dto.request.CreateMomentCommentRequest;
import fit.iuh.cnm_project_be.social.dto.request.CreatePostCommentRequest;
import fit.iuh.cnm_project_be.social.dto.request.CreatePostRequest;
import fit.iuh.cnm_project_be.social.dto.response.CommunityVideoFeedResponse;
import fit.iuh.cnm_project_be.social.dto.response.MomentCommentResponse;
import fit.iuh.cnm_project_be.social.dto.response.MomentResponse;
import fit.iuh.cnm_project_be.social.dto.response.PostAudienceResponse;
import fit.iuh.cnm_project_be.social.dto.response.PostCommentResponse;
import fit.iuh.cnm_project_be.social.dto.response.PostInteractionResponse;
import fit.iuh.cnm_project_be.social.dto.response.PostResponse;
import fit.iuh.cnm_project_be.social.dto.response.SocialMediaUploadResponse;
import fit.iuh.cnm_project_be.social.enums.PostVisibilityMode;
import fit.iuh.cnm_project_be.social.service.SocialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @GetMapping("/posts/feed")
    public ApiResponse<List<PostResponse>> getPostFeed(@RequestParam(required = false) Integer size) {
        return ApiResponse.ok(socialService.getFriendPostFeed(size), UUID.randomUUID().toString());
    }

    @GetMapping("/posts/me")
    public ApiResponse<List<PostResponse>> getMyPosts(
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(socialService.getMyPosts(archived, size), UUID.randomUUID().toString());
    }

    @PostMapping("/posts")
    public ApiResponse<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.ok(socialService.createPost(request), UUID.randomUUID().toString());
    }

    @PostMapping(value = "/posts/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PostResponse> createPostWithUpload(
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) String caption,
            @RequestParam(defaultValue = "ALL_FRIENDS") PostVisibilityMode visibilityMode,
            @RequestParam(required = false) List<UUID> allowedViewerIds,
            @RequestParam(required = false) List<UUID> taggedFriendIds) {
        MultipartFile[] resolvedFiles = files;
        if ((resolvedFiles == null || resolvedFiles.length == 0) && file != null && !file.isEmpty()) {
            resolvedFiles = new MultipartFile[]{file};
        }
        return ApiResponse.ok(
                socialService.createPost(resolvedFiles, caption, visibilityMode, allowedViewerIds, taggedFriendIds),
                UUID.randomUUID().toString()
        );
    }

    @PatchMapping("/posts/{postId}/archive")
    public ApiResponse<PostResponse> archivePost(@PathVariable UUID postId) {
        return ApiResponse.ok(socialService.archivePost(postId), UUID.randomUUID().toString());
    }

    @PatchMapping("/posts/{postId}/restore")
    public ApiResponse<PostResponse> restorePost(@PathVariable UUID postId) {
        return ApiResponse.ok(socialService.restorePost(postId), UUID.randomUUID().toString());
    }

    @DeleteMapping("/posts/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable UUID postId) {
        socialService.deletePost(postId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PutMapping("/posts/{postId}/like")
    public ApiResponse<PostResponse> likePost(@PathVariable UUID postId) {
        return ApiResponse.ok(socialService.likePost(postId), UUID.randomUUID().toString());
    }

    @DeleteMapping("/posts/{postId}/like")
    public ApiResponse<PostResponse> unlikePost(@PathVariable UUID postId) {
        return ApiResponse.ok(socialService.unlikePost(postId), UUID.randomUUID().toString());
    }

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<PostCommentResponse> addComment(
            @PathVariable UUID postId,
            @Valid @RequestBody CreatePostCommentRequest request) {
        return ApiResponse.ok(socialService.addComment(postId, request), UUID.randomUUID().toString());
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<PostCommentResponse>> getPostComments(@PathVariable UUID postId) {
        return ApiResponse.ok(socialService.getPostComments(postId), UUID.randomUUID().toString());
    }

    @GetMapping("/posts/{postId}/interactions")
    public ApiResponse<PostInteractionResponse> getPostInteractions(@PathVariable UUID postId) {
        return ApiResponse.ok(socialService.getPostInteractions(postId), UUID.randomUUID().toString());
    }

    @GetMapping("/posts/{postId}/audience")
    public ApiResponse<PostAudienceResponse> getPostAudience(@PathVariable UUID postId) {
        return ApiResponse.ok(socialService.getPostAudience(postId), UUID.randomUUID().toString());
    }

    @PostMapping("/comments/{commentId}/replies")
    public ApiResponse<PostCommentResponse> replyToComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody CreatePostCommentRequest request) {
        return ApiResponse.ok(socialService.replyToComment(commentId, request), UUID.randomUUID().toString());
    }

    @PutMapping("/comments/{commentId}/like")
    public ApiResponse<PostCommentResponse> likeComment(@PathVariable UUID commentId) {
        return ApiResponse.ok(socialService.likeComment(commentId), UUID.randomUUID().toString());
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ApiResponse<PostCommentResponse> unlikeComment(@PathVariable UUID commentId) {
        return ApiResponse.ok(socialService.unlikeComment(commentId), UUID.randomUUID().toString());
    }

    @PostMapping("/moments")
    public ApiResponse<MomentResponse> createMoment(@Valid @RequestBody CreateMomentRequest request) {
        return ApiResponse.ok(socialService.createMoment(request), UUID.randomUUID().toString());
    }

    @GetMapping("/moments/me")
    public ApiResponse<List<MomentResponse>> getMyMoments(@RequestParam(required = false) Integer size) {
        return ApiResponse.ok(socialService.getMyMoments(size), UUID.randomUUID().toString());
    }

    @GetMapping("/moments/feed")
    public ApiResponse<List<MomentResponse>> getStoryFeed(@RequestParam(required = false) Integer size) {
        return ApiResponse.ok(socialService.getStoryFeed(size), UUID.randomUUID().toString());
    }

    @DeleteMapping("/moments/{momentId}")
    public ApiResponse<Void> deleteMoment(@PathVariable UUID momentId) {
        socialService.deleteMoment(momentId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @GetMapping("/videos/feed")
    public ApiResponse<List<MomentResponse>> getVideoFeed(@RequestParam(required = false) Integer size) {
        return ApiResponse.ok(socialService.getRandomVideoFeed(size), UUID.randomUUID().toString());
    }

    @GetMapping("/videos/community-feed")
    public ApiResponse<CommunityVideoFeedResponse> getCommunityVideoFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(socialService.getCommunityVideoFeed(cursor, size), UUID.randomUUID().toString());
    }

    @PutMapping("/videos/{momentId}/like")
    public ApiResponse<MomentResponse> likeVideo(@PathVariable UUID momentId) {
        return ApiResponse.ok(socialService.likeVideo(momentId), UUID.randomUUID().toString());
    }

    @DeleteMapping("/videos/{momentId}/like")
    public ApiResponse<MomentResponse> unlikeVideo(@PathVariable UUID momentId) {
        return ApiResponse.ok(socialService.unlikeVideo(momentId), UUID.randomUUID().toString());
    }

    @GetMapping("/videos/{momentId}/comments")
    public ApiResponse<List<MomentCommentResponse>> getMomentComments(@PathVariable UUID momentId) {
        return ApiResponse.ok(socialService.getMomentComments(momentId), UUID.randomUUID().toString());
    }

    @PostMapping("/videos/{momentId}/comments")
    public ApiResponse<MomentCommentResponse> addMomentComment(
            @PathVariable UUID momentId,
            @Valid @RequestBody CreateMomentCommentRequest request) {
        return ApiResponse.ok(socialService.addMomentComment(momentId, request), UUID.randomUUID().toString());
    }

    @PostMapping("/videos/{momentId}/view")
    public ApiResponse<Void> recordMomentView(@PathVariable UUID momentId) {
        socialService.recordMomentView(momentId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PostMapping(value = "/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SocialMediaUploadResponse> uploadMedia(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(socialService.uploadMedia(file), UUID.randomUUID().toString());
    }
}
