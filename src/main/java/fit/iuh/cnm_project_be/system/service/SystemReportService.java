package fit.iuh.cnm_project_be.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.admin.service.AdminDashboardService;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.social.repository.MomentRepository;
import fit.iuh.cnm_project_be.social.repository.PostRepository;
import fit.iuh.cnm_project_be.system.dto.request.CreateReportRequest;
import fit.iuh.cnm_project_be.system.dto.response.ReportResponse;
import fit.iuh.cnm_project_be.system.entity.ActivityLog;
import fit.iuh.cnm_project_be.system.entity.Report;
import fit.iuh.cnm_project_be.system.enums.ReportTargetType;
import fit.iuh.cnm_project_be.system.repository.ActivityLogRepository;
import fit.iuh.cnm_project_be.system.repository.ReportRepository;
import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemReportService {

    private final ReportRepository reportRepository;
    private final ActivityLogRepository activityLogRepository;
    private final PostRepository postRepository;
    private final MomentRepository momentRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final AdminDashboardService adminDashboardService;

    @Transactional
    public ReportResponse createReport(CreateReportRequest request) {
        UUID reporterId = userService.getCurrentUserId();
        validateTarget(request.getTargetType(), request.getTargetId());

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(request.getTargetType());
        report.setTargetId(request.getTargetId().trim());
        report.setReason(request.getReason().trim());
        report.setAiAnalysis("{}");
        report.setActionTaken(null);
        report.setCreatedAt(Instant.now());
        report.setResolvedAt(null);

        Report saved = reportRepository.save(report);
        logActivity(reporterId, saved);
        adminDashboardService.publishReportCreatedEvent(saved);

        return ReportResponse.builder()
                .id(saved.getId())
                .targetType(saved.getTargetType())
                .targetId(saved.getTargetId())
                .reason(saved.getReason())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private void validateTarget(ReportTargetType targetType, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new BusinessException("targetId is required");
        }

        switch (targetType) {
            case USER -> UUID.fromString(targetId.trim());
            case POST -> {
                UUID postId = UUID.fromString(targetId.trim());
                if (postRepository.findById(postId).isEmpty()) {
                    throw new BusinessException("Post not found");
                }
            }
            case MOMENT -> {
                UUID momentId = UUID.fromString(targetId.trim());
                if (momentRepository.findById(momentId).isEmpty()) {
                    throw new BusinessException("Moment not found");
                }
            }
            case MESSAGE -> {
                Long messageId = Long.parseLong(targetId.trim());
                if (messageRepository.findByIdAndDeletedAtIsNull(messageId).isEmpty()) {
                    throw new BusinessException("Message not found");
                }
            }
            default -> throw new BusinessException("Unsupported target type");
        }
    }

    private void logActivity(UUID reporterId, Report report) {
        ActivityLog log = new ActivityLog();
        log.setUserId(reporterId);
        log.setAction("REPORT_CREATED");
        log.setMetadata(toJson(Map.of(
                "reportId", report.getId(),
                "targetType", report.getTargetType().name(),
                "targetId", report.getTargetId())));
        log.setCreatedAt(Instant.now());
        activityLogRepository.save(log);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}

