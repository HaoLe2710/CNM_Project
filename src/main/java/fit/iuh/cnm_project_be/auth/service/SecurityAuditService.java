package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.entity.SecurityAuditLog;
import fit.iuh.cnm_project_be.auth.repository.SecurityAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditLogRepository securityAuditLogRepository;

    @Transactional
    public void log(UUID userId, String eventType, String title, String detail, String deviceId, String platform) {
        SecurityAuditLog log = new SecurityAuditLog();
        log.setUserId(userId);
        log.setEventType(eventType);
        log.setTitle(title);
        log.setDetail(detail);
        log.setDeviceId(deviceId);
        log.setPlatform(platform);
        securityAuditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<SecurityAuditLog> getHistory(UUID userId, LocalDate month, int size) {
        if (month == null) {
            return securityAuditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size));
        }
        Instant from = month.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = month.plusMonths(1).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return securityAuditLogRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                userId,
                from,
                to,
                PageRequest.of(0, size)
        );
    }

    @Transactional(readOnly = true)
    public List<SecurityAuditLog> getLogoutHistory(UUID userId, int size) {
        return securityAuditLogRepository.findByUserIdAndEventTypeStartingWithOrderByCreatedAtDesc(
                userId,
                "DEVICE_",
                PageRequest.of(0, size)
        );
    }
}
