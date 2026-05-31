package fit.iuh.cnm_project_be.poll.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.poll.dto.request.CreatePollRequest;
import fit.iuh.cnm_project_be.poll.dto.request.PollOptionRequest;
import fit.iuh.cnm_project_be.poll.dto.request.UpdatePollRequest;
import fit.iuh.cnm_project_be.poll.dto.response.PollResponse;
import fit.iuh.cnm_project_be.poll.service.PollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @GetMapping("/api/v1/conversations/{conversationId}/polls")
    public ApiResponse<List<PollResponse>> list(
            @PathVariable UUID conversationId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(pollService.list(conversationId, currentUserId), UUID.randomUUID().toString());
    }

    @PostMapping("/api/v1/conversations/{conversationId}/polls")
    public ApiResponse<PollResponse> create(
            @PathVariable UUID conversationId,
            @Valid @RequestBody CreatePollRequest request,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(pollService.create(conversationId, currentUserId, request), UUID.randomUUID().toString());
    }

    @GetMapping("/api/v1/polls/{pollId}")
    public ApiResponse<PollResponse> get(
            @PathVariable UUID pollId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(pollService.get(pollId, currentUserId), UUID.randomUUID().toString());
    }

    @PatchMapping("/api/v1/polls/{pollId}")
    public ApiResponse<PollResponse> update(
            @PathVariable UUID pollId,
            @Valid @RequestBody UpdatePollRequest request,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(pollService.update(pollId, currentUserId, request), UUID.randomUUID().toString());
    }

    @DeleteMapping("/api/v1/polls/{pollId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID pollId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        pollService.delete(pollId, currentUserId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PostMapping("/api/v1/polls/{pollId}/close")
    public ApiResponse<PollResponse> close(
            @PathVariable UUID pollId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(pollService.close(pollId, currentUserId), UUID.randomUUID().toString());
    }

    @PostMapping("/api/v1/polls/{pollId}/options")
    public ApiResponse<PollResponse> addOption(
            @PathVariable UUID pollId,
            @Valid @RequestBody PollOptionRequest request,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(pollService.addOption(pollId, currentUserId, request), UUID.randomUUID().toString());
    }

    @PatchMapping("/api/v1/polls/{pollId}/options/{optionId}")
    public ApiResponse<PollResponse> updateOption(
            @PathVariable UUID pollId,
            @PathVariable UUID optionId,
            @Valid @RequestBody PollOptionRequest request,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(pollService.updateOption(pollId, optionId, currentUserId, request), UUID.randomUUID().toString());
    }

    @DeleteMapping("/api/v1/polls/{pollId}/options/{optionId}")
    public ApiResponse<PollResponse> deleteOption(
            @PathVariable UUID pollId,
            @PathVariable UUID optionId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(pollService.deleteOption(pollId, optionId, currentUserId), UUID.randomUUID().toString());
    }

    @PostMapping("/api/v1/polls/{pollId}/options/{optionId}/votes")
    public ApiResponse<PollResponse> vote(
            @PathVariable UUID pollId,
            @PathVariable UUID optionId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(pollService.vote(pollId, optionId, currentUserId), UUID.randomUUID().toString());
    }

    @DeleteMapping("/api/v1/polls/{pollId}/options/{optionId}/votes")
    public ApiResponse<PollResponse> unvote(
            @PathVariable UUID pollId,
            @PathVariable UUID optionId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(pollService.unvote(pollId, optionId, currentUserId), UUID.randomUUID().toString());
    }
}
