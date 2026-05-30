package fit.iuh.cnm_project_be.system.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.system.dto.request.CreateReportRequest;
import fit.iuh.cnm_project_be.system.dto.response.ReportResponse;
import fit.iuh.cnm_project_be.system.service.SystemReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class SystemReportController {

    private final SystemReportService systemReportService;

    @PostMapping
    public ApiResponse<ReportResponse> createReport(@Valid @RequestBody CreateReportRequest request) {
        return ApiResponse.ok(systemReportService.createReport(request), UUID.randomUUID().toString());
    }
}

