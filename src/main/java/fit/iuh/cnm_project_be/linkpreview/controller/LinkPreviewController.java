package fit.iuh.cnm_project_be.linkpreview.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.linkpreview.dto.LinkPreviewResponse;
import fit.iuh.cnm_project_be.linkpreview.service.LinkPreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/link-preview")
@RequiredArgsConstructor
public class LinkPreviewController {

    private final LinkPreviewService linkPreviewService;

    @GetMapping
    public ApiResponse<LinkPreviewResponse> extract(@RequestParam String url) {
        return ApiResponse.ok(linkPreviewService.extract(url), UUID.randomUUID().toString());
    }
}
