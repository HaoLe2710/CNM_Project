package fit.iuh.cnm_project_be.admin.controller;

import fit.iuh.cnm_project_be.admin.dto.request.AdminModerateUserRequest;
import fit.iuh.cnm_project_be.admin.dto.request.AdminResolveReportRequest;
import fit.iuh.cnm_project_be.admin.dto.response.AdminActionResponse;
import fit.iuh.cnm_project_be.admin.dto.response.AdminAlertResponse;
import fit.iuh.cnm_project_be.admin.dto.response.AdminDashboardSummaryResponse;
import fit.iuh.cnm_project_be.admin.dto.response.AdminLogItemResponse;
import fit.iuh.cnm_project_be.admin.dto.response.AdminReportItemResponse;
import fit.iuh.cnm_project_be.admin.dto.response.AdminUserItemResponse;
import fit.iuh.cnm_project_be.admin.service.AdminDashboardService;
import fit.iuh.cnm_project_be.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/summary")
    public ApiResponse<AdminDashboardSummaryResponse> getSummary() {
        return ApiResponse.ok(adminDashboardService.getSummary(), UUID.randomUUID().toString());
    }

    @GetMapping("/alerts")
    public ApiResponse<List<AdminAlertResponse>> getAlerts(@RequestParam(required = false) Integer size) {
        return ApiResponse.ok(adminDashboardService.getAlerts(size), UUID.randomUUID().toString());
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserItemResponse>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(adminDashboardService.getUsers(keyword, status, page, size), UUID.randomUUID().toString());
    }

    @PatchMapping("/users/{userId}/moderation")
    public ApiResponse<AdminActionResponse> moderateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminModerateUserRequest request
    ) {
        return ApiResponse.ok(adminDashboardService.moderateUser(userId, request), UUID.randomUUID().toString());
    }

    @GetMapping("/reports")
    public ApiResponse<List<AdminReportItemResponse>> getReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(adminDashboardService.getReports(status, targetType, page, size), UUID.randomUUID().toString());
    }

    @PatchMapping("/reports/{reportId}/resolve")
    public ApiResponse<AdminActionResponse> resolveReport(
            @PathVariable Long reportId,
            @Valid @RequestBody AdminResolveReportRequest request
    ) {
        return ApiResponse.ok(adminDashboardService.resolveReport(reportId, request), UUID.randomUUID().toString());
    }

    @GetMapping("/logs")
    public ApiResponse<List<AdminLogItemResponse>> getLogs(
            @RequestParam(required = false, defaultValue = "ALL") String scope,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(adminDashboardService.getLogs(scope, size), UUID.randomUUID().toString());
    }
}

