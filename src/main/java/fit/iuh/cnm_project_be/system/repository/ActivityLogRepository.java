package fit.iuh.cnm_project_be.system.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.system.entity.ActivityLog;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface ActivityLogRepository
        extends BaseRepository<ActivityLog, Long> {

    List<ActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(Instant fromTime, Pageable pageable);

    List<ActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
