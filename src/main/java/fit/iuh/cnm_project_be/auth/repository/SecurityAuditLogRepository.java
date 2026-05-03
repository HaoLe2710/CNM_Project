package fit.iuh.cnm_project_be.auth.repository;

import fit.iuh.cnm_project_be.auth.entity.SecurityAuditLog;
import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SecurityAuditLogRepository extends BaseRepository<SecurityAuditLog, Long> {
    List<SecurityAuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<SecurityAuditLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID userId,
            Instant from,
            Instant to,
            Pageable pageable
    );

    List<SecurityAuditLog> findByUserIdAndEventTypeStartingWithOrderByCreatedAtDesc(
            UUID userId,
            String eventTypePrefix,
            Pageable pageable
    );
}
