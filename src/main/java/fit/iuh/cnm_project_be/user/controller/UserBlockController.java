package fit.iuh.cnm_project_be.user.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.user.dto.request.BlockUserRequest;
import fit.iuh.cnm_project_be.user.dto.response.UserBlockResponse;
import fit.iuh.cnm_project_be.user.service.UserBlockService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/blocks")
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserBlockController {

    UserBlockService userBlockService;

    @PostMapping
    public ApiResponse<UserBlockResponse> blockUser(@Valid @RequestBody BlockUserRequest request) {
        return ApiResponse.ok(
                userBlockService.blockUser(request.getBlockedUserId(), request.getReason()),
                UUID.randomUUID().toString()
        );
    }

    @DeleteMapping("/{blockedUserId}")
    public ApiResponse<String> unblockUser(@PathVariable UUID blockedUserId) {
        userBlockService.unblockUser(blockedUserId);
        return ApiResponse.ok("Unblock successfully", UUID.randomUUID().toString());
    }

    @GetMapping
    public ApiResponse<List<UserBlockResponse>> getBlockedUsers() {
        return ApiResponse.ok(
                userBlockService.getBlockedUsers(),
                UUID.randomUUID().toString()
        );
    }
}
