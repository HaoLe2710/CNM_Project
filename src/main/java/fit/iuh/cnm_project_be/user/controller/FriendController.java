package fit.iuh.cnm_project_be.user.controller;


import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.user.dto.request.SendFriendRequestRequest;
import fit.iuh.cnm_project_be.user.dto.request.UpdateFriendshipSettingRequest;
import fit.iuh.cnm_project_be.user.dto.response.FriendRequestResponse;
import fit.iuh.cnm_project_be.user.dto.response.FriendshipResponse;
import fit.iuh.cnm_project_be.user.dto.response.FriendshipSettingResponse;
import fit.iuh.cnm_project_be.user.service.FriendService;
import fit.iuh.cnm_project_be.user.service.FriendshipSettingService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/friends")
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendController {
    FriendService friendService;
    FriendshipSettingService friendshipSettingService;

    @GetMapping
    public ApiResponse<List<FriendshipResponse>> getFriends(
            @RequestParam(defaultValue = "false") boolean closeOnly) {
        return ApiResponse.ok(friendService.getFriends(closeOnly), UUID.randomUUID().toString());
    }

    @GetMapping("/close")
    public ApiResponse<List<FriendshipResponse>> getCloseFriends() {
        return ApiResponse.ok(friendshipSettingService.listMyCloseFriends(), UUID.randomUUID().toString());
    }

    @GetMapping("/settings")
    public ApiResponse<List<FriendshipSettingResponse>> getMyFriendshipSettings() {
        return ApiResponse.ok(friendshipSettingService.getMyFriendshipSettings(), UUID.randomUUID().toString());
    }

    @GetMapping("/{friendId}/settings")
    public ApiResponse<FriendshipSettingResponse> getFriendshipSetting(@PathVariable UUID friendId) {
        return ApiResponse.ok(friendshipSettingService.getMyFriendshipSetting(friendId), UUID.randomUUID().toString());
    }

    @PatchMapping("/{friendId}/settings")
    public ApiResponse<FriendshipSettingResponse> updateFriendshipSetting(
            @PathVariable UUID friendId,
            @Valid @RequestBody(required = false) UpdateFriendshipSettingRequest request) {
        return ApiResponse.ok(
                friendshipSettingService.updateMyFriendshipSetting(friendId, request),
                UUID.randomUUID().toString());
    }

    @PostMapping("/requests")
    public ApiResponse<FriendRequestResponse> sendFriendRequest(@Valid @RequestBody SendFriendRequestRequest request) {
        return ApiResponse.ok(friendService.sendRequest(request.getReceiverId()), UUID.randomUUID().toString());
    }

    @GetMapping("/requests/incoming")
    public ApiResponse<List<FriendRequestResponse>> getIncomingRequests() {
        return ApiResponse.ok(friendService.getIncomingRequests(), UUID.randomUUID().toString());
    }

    @GetMapping("/requests/outgoing")
    public ApiResponse<List<FriendRequestResponse>> getOutgoingRequests() {
        return ApiResponse.ok(friendService.getOutgoingRequests(), UUID.randomUUID().toString());
    }

    @PostMapping("/requests/{requestId}/accept")
    public ApiResponse<FriendRequestResponse> acceptRequest(@PathVariable Long requestId) {
        return ApiResponse.ok(friendService.acceptRequest(requestId), UUID.randomUUID().toString());
    }

    @PostMapping("/requests/{requestId}/reject")
    public ApiResponse<FriendRequestResponse> rejectRequest(@PathVariable Long requestId) {
        return ApiResponse.ok(friendService.rejectRequest(requestId), UUID.randomUUID().toString());
    }

    @DeleteMapping("/{friendUserId}")
    public ApiResponse<String> unfriend(@PathVariable UUID friendUserId) {
        friendService.unfriend(friendUserId);
        return ApiResponse.ok("Unfriend successfully", UUID.randomUUID().toString());
    }


}
