package fit.iuh.cnm_project_be.ringback.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.ringback.dto.request.UpdateRingbackRequest;
import fit.iuh.cnm_project_be.ringback.dto.response.RingbackResponse;
import fit.iuh.cnm_project_be.ringback.service.RingbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ringback")
@RequiredArgsConstructor
public class RingbackController {

    private final RingbackService ringbackService;

    @GetMapping("/me")
    public ApiResponse<RingbackResponse> getMine() {
        return ApiResponse.ok(ringbackService.getMine(), UUID.randomUUID().toString());
    }

    @PatchMapping("/me")
    public ApiResponse<RingbackResponse> updateMine(@RequestBody UpdateRingbackRequest request) {
        return ApiResponse.ok(ringbackService.updateMine(request), UUID.randomUUID().toString());
    }
}
