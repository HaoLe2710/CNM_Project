package fit.iuh.cnm_project_be.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.admin.dto.request.AdminModerateUserRequest;
import fit.iuh.cnm_project_be.admin.dto.request.AdminResolveReportRequest;
import fit.iuh.cnm_project_be.admin.dto.response.AdminActionResponse;
import fit.iuh.cnm_project_be.admin.dto.response.AdminAlertResponse;
import fit.iuh.cnm_project_be.admin.dto.response.AdminDashboardSummaryResponse;
import fit.iuh.cnm_project_be.admin.dto.response.AdminLogItemResponse;
import fit.iuh.cnm_project_be.admin.dto.response.AdminReportItemResponse;
import fit.iuh.cnm_project_be.admin.dto.response.AdminUserItemResponse;
import fit.iuh.cnm_project_be.auth.entity.SecurityAuditLog;
import fit.iuh.cnm_project_be.auth.repository.AccountRepository;
import fit.iuh.cnm_project_be.auth.repository.SecurityAuditLogRepository;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.social.entity.Moment;
import fit.iuh.cnm_project_be.social.entity.Post;
import fit.iuh.cnm_project_be.social.enums.MediaType;
import fit.iuh.cnm_project_be.social.enums.MomentVisibilityMode;
import fit.iuh.cnm_project_be.social.repository.MomentRepository;
import fit.iuh.cnm_project_be.social.repository.PostRepository;
import fit.iuh.cnm_project_be.system.entity.ActivityLog;
import fit.iuh.cnm_project_be.system.entity.Report;
import fit.iuh.cnm_project_be.system.enums.ReportTargetType;
import fit.iuh.cnm_project_be.system.repository.ActivityLogRepository;
import fit.iuh.cnm_project_be.system.repository.ReportRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final String ADMIN_DASHBOARD_TOPIC = "/topic/admin/dashboard";
    private static final String ADMIN_REPORTS_TOPIC = "/topic/admin/reports";
    private static final String ADMIN_USERS_TOPIC = "/topic/admin/users";

    private final UserProfileRepository userProfileRepository;
    private final AccountRepository accountRepository;
    private final PostRepository postRepository;
    private final MomentRepository momentRepository;
    private final ReportRepository reportRepository;
    private final ActivityLogRepository activityLogRepository;
    private final SecurityAuditLogRepository securityAuditLogRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {
        Instant now = Instant.now();
        long totalUsers = userProfileRepository.countByDeletedAtIsNull();
        long bannedUsers = userProfileRepository.countByDeletedAtIsNullAndBannedUntilAfter(now);
        long totalPosts = postRepository.countByDeletedAtIsNullAndArchivedAtIsNull();
        long totalPublicVideos = momentRepository.countByMediaTypeAndVisibilityModeAndDeletedAtIsNull(
                MediaType.VIDEO, MomentVisibilityMode.PUBLIC);
        long pendingReports = reportRepository.countByResolvedAtIsNull();
        long reportsLast24h = reportRepository.countByCreatedAtAfter(now.minus(24, ChronoUnit.HOURS));

        return AdminDashboardSummaryResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(Math.max(totalUsers - bannedUsers, 0))
                .bannedUsers(bannedUsers)
                .totalPosts(totalPosts)
                .totalPublicVideos(totalPublicVideos)
                .pendingReports(pendingReports)
                .reportsLast24h(reportsLast24h)
                .alerts(buildAlerts(now))
                .generatedAt(now)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminAlertResponse> getAlerts(Integer size) {
        return buildAlerts(Instant.now()).stream()
                .limit(normalizeSize(size, 20, 100))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminUserItemResponse> getUsers(String keyword, String status, Integer page, Integer size) {
        int safePage = Math.max(page == null ? 0 : page, 0);
        int safeSize = normalizeSize(size, 20, 100);
        boolean bannedOnly = "BANNED".equalsIgnoreCase(status);
        Instant now = Instant.now();

        return userProfileRepository.findForAdmin(cleanKeyword(keyword), bannedOnly, now, PageRequest.of(safePage, safeSize))
                .stream()
                .map(profile -> toUserItem(profile, now))
                .toList();
    }

    @Transactional
    public AdminActionResponse moderateUser(UUID userId, AdminModerateUserRequest request) {
        UserProfile target = userProfileRepository.findById(userId)
                .filter(profile -> profile.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String action = normalizeAction(request.getAction());
        Instant now = Instant.now();
        if ("BAN".equals(action)) {
            int hours = request.getBanHours() == null || request.getBanHours() <= 0 ? 24 : request.getBanHours();
            target.setBannedUntil(now.plus(hours, ChronoUnit.HOURS));
            userProfileRepository.save(target);
            saveActivity(target.getUserId(), "ADMIN_USER_BANNED", Map.of(
                    "reason", normalizeNullable(request.getReason()),
                    "banHours", hours));
            broadcastAdminEvent(RealtimeEventType.ADMIN_USER_MODERATED, Map.of(
                    "action", "BAN",
                    "userId", target.getUserId(),
                    "bannedUntil", target.getBannedUntil()));
            return AdminActionResponse.builder()
                    .success(true)
                    .message("User banned successfully")
                    .processedAt(now)
                    .build();
        }
        if ("UNBAN".equals(action)) {
            target.setBannedUntil(null);
            userProfileRepository.save(target);
            saveActivity(target.getUserId(), "ADMIN_USER_UNBANNED", Map.of(
                    "reason", normalizeNullable(request.getReason())));
            broadcastAdminEvent(RealtimeEventType.ADMIN_USER_MODERATED, Map.of(
                    "action", "UNBAN",
                    "userId", target.getUserId()));
            return AdminActionResponse.builder()
                    .success(true)
                    .message("User unbanned successfully")
                    .processedAt(now)
                    .build();
        }
        throw new BusinessException("Unsupported action. Use BAN or UNBAN");
    }

    @Transactional(readOnly = true)
    public List<AdminReportItemResponse> getReports(String status, String targetType, Integer page, Integer size) {
        int safePage = Math.max(page == null ? 0 : page, 0);
        int safeSize = normalizeSize(size, 20, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        List<Report> reports;
        ReportTargetType parsedTargetType = parseReportTargetType(targetType);
        boolean pendingOnly = status == null || status.isBlank() || "PENDING".equalsIgnoreCase(status);

        if (pendingOnly) {
            reports = parsedTargetType == null
                    ? reportRepository.findByResolvedAtIsNullOrderByCreatedAtDesc(pageable)
                    : reportRepository.findByResolvedAtIsNullAndTargetTypeOrderByCreatedAtDesc(parsedTargetType, pageable);
        } else {
            reports = reportRepository.findByResolvedAtIsNotNullOrderByResolvedAtDesc(pageable);
            if (parsedTargetType != null) {
                reports = reports.stream().filter(r -> r.getTargetType() == parsedTargetType).toList();
            }
        }

        return reports.stream().map(this::toReportItem).toList();
    }

    @Transactional
    public AdminActionResponse resolveReport(Long reportId, AdminResolveReportRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));

        String action = normalizeAction(request.getAction());
        Instant now = Instant.now();
        applyReportAction(report, action, request);

        report.setActionTaken(action + appendNote(request.getNote()));
        report.setResolvedAt(now);
        reportRepository.save(report);

        saveActivity(resolveTargetUserId(report), "ADMIN_REPORT_RESOLVED", Map.of(
                "reportId", report.getId(),
                "action", action,
                "targetType", report.getTargetType().name(),
                "targetId", report.getTargetId()));

        broadcastAdminEvent(RealtimeEventType.ADMIN_REPORT_RESOLVED, Map.of(
                "reportId", report.getId(),
                "action", action,
                "targetType", report.getTargetType().name(),
                "targetId", report.getTargetId()));

        return AdminActionResponse.builder()
                .success(true)
                .message("Report resolved successfully")
                .processedAt(now)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminLogItemResponse> getLogs(String scope, Integer size) {
        int safeSize = normalizeSize(size, 50, 200);
        String normalizedScope = scope == null ? "ALL" : scope.trim().toUpperCase(Locale.ROOT);
        PageRequest pageable = PageRequest.of(0, safeSize);
        List<AdminLogItemResponse> items = new ArrayList<>();

        if ("ALL".equals(normalizedScope) || "ACTIVITY".equals(normalizedScope)) {
            for (ActivityLog log : activityLogRepository.findAllByOrderByCreatedAtDesc(pageable)) {
                items.add(AdminLogItemResponse.builder()
                        .source("ACTIVITY")
                        .id(log.getId())
                        .userId(log.getUserId())
                        .eventType(log.getAction())
                        .title(log.getAction())
                        .detail(log.getAction())
                        .metadata(log.getMetadata())
                        .createdAt(log.getCreatedAt())
                        .build());
            }
        }

        if ("ALL".equals(normalizedScope) || "SECURITY".equals(normalizedScope)) {
            for (SecurityAuditLog log : securityAuditLogRepository.findAllByOrderByCreatedAtDesc(pageable)) {
                items.add(AdminLogItemResponse.builder()
                        .source("SECURITY")
                        .id(log.getId())
                        .userId(log.getUserId())
                        .eventType(log.getEventType())
                        .title(log.getTitle())
                        .detail(log.getDetail())
                        .metadata(null)
                        .createdAt(log.getCreatedAt())
                        .build());
            }
        }

        return items.stream()
                .sorted(Comparator.comparing(AdminLogItemResponse::getCreatedAt).reversed())
                .limit(safeSize)
                .toList();
    }

    @Transactional
    public void publishReportCreatedEvent(Report report) {
        broadcastAdminEvent(RealtimeEventType.ADMIN_REPORT_CREATED, Map.of(
                "reportId", report.getId(),
                "targetType", report.getTargetType().name(),
                "targetId", report.getTargetId(),
                "createdAt", report.getCreatedAt()));
    }

    private List<AdminAlertResponse> buildAlerts(Instant now) {
        List<AdminAlertResponse> alerts = new ArrayList<>();
        long pendingReports = reportRepository.countByResolvedAtIsNull();
        if (pendingReports > 0) {
            alerts.add(AdminAlertResponse.builder()
                    .type("REPORT")
                    .severity(pendingReports > 30 ? "HIGH" : "MEDIUM")
                    .title("Pending reports")
                    .detail("There are " + pendingReports + " unresolved reports")
                    .createdAt(now)
                    .build());
        }

        long bannedUsers = userProfileRepository.countByDeletedAtIsNullAndBannedUntilAfter(now);
        if (bannedUsers > 0) {
            alerts.add(AdminAlertResponse.builder()
                    .type("USER")
                    .severity("LOW")
                    .title("Banned users")
                    .detail(bannedUsers + " users are currently banned")
                    .createdAt(now)
                    .build());
        }

        long pendingPostReports = reportRepository.countByTargetTypeAndResolvedAtIsNull(ReportTargetType.POST);
        if (pendingPostReports > 0) {
            alerts.add(AdminAlertResponse.builder()
                    .type("POST")
                    .severity(pendingPostReports > 10 ? "HIGH" : "MEDIUM")
                    .title("Post moderation queue")
                    .detail("There are " + pendingPostReports + " pending post reports")
                    .createdAt(now)
                    .build());
        }
        return alerts;
    }

    private AdminUserItemResponse toUserItem(UserProfile profile, Instant now) {
        return AdminUserItemResponse.builder()
                .userId(profile.getUserId())
                .username(profile.getUsername())
                .displayName(profile.getDisplayName())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .avatarUrl(profile.getAvatarUrl())
                .createdAt(profile.getCreatedAt())
                .bannedUntil(profile.getBannedUntil())
                .bannedNow(profile.getBannedUntil() != null && profile.getBannedUntil().isAfter(now))
                .build();
    }

    private AdminReportItemResponse toReportItem(Report report) {
        UserProfile reporter = userProfileRepository.findById(report.getReporterId()).orElse(null);
        return AdminReportItemResponse.builder()
                .reportId(report.getId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .actionTaken(report.getActionTaken())
                .reporterDisplayName(reporter == null ? null : reporter.getDisplayName())
                .reporterUsername(reporter == null ? null : reporter.getUsername())
                .createdAt(report.getCreatedAt())
                .resolvedAt(report.getResolvedAt())
                .build();
    }

    private void applyReportAction(Report report, String action, AdminResolveReportRequest request) {
        switch (action) {
            case "DISMISS" -> {
            }
            case "WARN_USER" -> saveActivity(resolveTargetUserId(report), "ADMIN_WARNING_ISSUED", Map.of(
                    "reportId", report.getId(),
                    "reason", normalizeNullable(report.getReason()),
                    "note", normalizeNullable(request.getNote())));
            case "BAN_USER" -> banReportTargetUser(report, request);
            case "REMOVE_POST" -> removePost(report);
            case "REMOVE_MOMENT" -> removeMoment(report);
            case "REMOVE_MESSAGE" -> removeMessage(report);
            default -> throw new BusinessException("Unsupported action");
        }
    }

    private void banReportTargetUser(Report report, AdminResolveReportRequest request) {
        UUID targetUserId = resolveTargetUserId(report);
        UserProfile target = userProfileRepository.findById(targetUserId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Target user not found"));
        int hours = request.getBanHours() == null || request.getBanHours() <= 0 ? 24 : request.getBanHours();
        target.setBannedUntil(Instant.now().plus(hours, ChronoUnit.HOURS));
        userProfileRepository.save(target);
    }

    private void removePost(Report report) {
        if (report.getTargetType() != ReportTargetType.POST) {
            throw new BusinessException("Target type mismatch for REMOVE_POST");
        }
        UUID postId = parseUuid(report.getTargetId(), "Invalid post id");
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        post.setDeletedAt(Instant.now());
        postRepository.save(post);
    }

    private void removeMoment(Report report) {
        if (report.getTargetType() != ReportTargetType.MOMENT) {
            throw new BusinessException("Target type mismatch for REMOVE_MOMENT");
        }
        UUID momentId = parseUuid(report.getTargetId(), "Invalid moment id");
        Moment moment = momentRepository.findById(momentId).orElseThrow(() -> new NotFoundException("Moment not found"));
        moment.setDeletedAt(Instant.now());
        momentRepository.save(moment);
    }

    private void removeMessage(Report report) {
        if (report.getTargetType() != ReportTargetType.MESSAGE) {
            throw new BusinessException("Target type mismatch for REMOVE_MESSAGE");
        }
        Long messageId;
        try {
            messageId = Long.parseLong(report.getTargetId());
        } catch (NumberFormatException ex) {
            throw new BusinessException("Invalid message id");
        }
        Message message = messageRepository.findByIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        message.setDeletedAt(Instant.now());
        messageRepository.save(message);
    }

    private UUID resolveTargetUserId(Report report) {
        try {
            return switch (report.getTargetType()) {
                case USER -> parseUuid(report.getTargetId(), "Invalid user id");
                case POST -> postRepository.findById(parseUuid(report.getTargetId(), "Invalid post id"))
                        .map(Post::getUserId)
                        .orElseThrow(() -> new NotFoundException("Post not found"));
                case MOMENT -> momentRepository.findById(parseUuid(report.getTargetId(), "Invalid moment id"))
                        .map(Moment::getUserId)
                        .orElseThrow(() -> new NotFoundException("Moment not found"));
                case MESSAGE -> messageRepository.findByIdAndDeletedAtIsNull(parseLong(report.getTargetId(), "Invalid message id"))
                        .map(Message::getSenderId)
                        .orElseThrow(() -> new NotFoundException("Message not found"));
            };
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Cannot resolve target user");
        }
    }

    private ReportTargetType parseReportTargetType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ReportTargetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid targetType");
        }
    }

    private String normalizeAction(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Action is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private int normalizeSize(Integer size, int defaultSize, int maxSize) {
        if (size == null || size <= 0) {
            return defaultSize;
        }
        return Math.min(size, maxSize);
    }

    private UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(message);
        }
    }

    private Long parseLong(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(message);
        }
    }

    private String appendNote(String note) {
        if (note == null || note.isBlank()) {
            return "";
        }
        return " | " + note.trim();
    }

    private void saveActivity(UUID targetUserId, String action, Map<String, Object> metadata) {
        ActivityLog log = new ActivityLog();
        log.setUserId(targetUserId);
        log.setAction(action);
        log.setMetadata(toJson(metadata));
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

    private void broadcastAdminEvent(RealtimeEventType type, Map<String, Object> payload) {
        Map<String, Object> normalizedPayload = new HashMap<>(payload);
        normalizedPayload.put("at", Instant.now());
        RealtimeEvent<Map<String, Object>> event = RealtimeEvent.of(type, normalizedPayload);
        messagingTemplate.convertAndSend(ADMIN_DASHBOARD_TOPIC, event);
        if (type == RealtimeEventType.ADMIN_REPORT_CREATED || type == RealtimeEventType.ADMIN_REPORT_RESOLVED) {
            messagingTemplate.convertAndSend(ADMIN_REPORTS_TOPIC, event);
        }
        if (type == RealtimeEventType.ADMIN_USER_MODERATED) {
            messagingTemplate.convertAndSend(ADMIN_USERS_TOPIC, event);
        }
    }

    private String cleanKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeNullable(String value) {
        return value == null ? "" : value;
    }
}
