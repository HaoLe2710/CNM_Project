package fit.iuh.cnm_project_be.zstyle.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.zstyle.dto.request.UpdateZStyleRequest;
import fit.iuh.cnm_project_be.zstyle.dto.response.ZStyleResponse;
import fit.iuh.cnm_project_be.zstyle.service.ZStyleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/zstyle")
@RequiredArgsConstructor
public class ZStyleController {

    private final ZStyleService zStyleService;

    @GetMapping("/me")
    public ApiResponse<ZStyleResponse> getMine() {
        return ApiResponse.ok(zStyleService.getMine(), UUID.randomUUID().toString());
    }

    @PatchMapping("/me")
    public ApiResponse<ZStyleResponse> updateMine(@RequestBody UpdateZStyleRequest request) {
        return ApiResponse.ok(zStyleService.updateMine(request), UUID.randomUUID().toString());
    }
}
