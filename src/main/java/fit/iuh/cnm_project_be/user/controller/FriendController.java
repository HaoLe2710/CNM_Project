package fit.iuh.cnm_project_be.user.controller;


import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.user.dto.request.SendFriendRequestRequest;
import fit.iuh.cnm_project_be.user.dto.response.FriendRequestResponse;
import fit.iuh.cnm_project_be.user.dto.response.FriendshipResponse;
import fit.iuh.cnm_project_be.user.service.FriendService;
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

    @GetMapping
    public ApiResponse<List<FriendshipResponse>> getFriends() {
        return ApiResponse.ok(friendService.getFriends(), UUID.randomUUID().toString());
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
