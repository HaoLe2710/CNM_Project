package fit.iuh.cnm_project_be.system.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.system.entity.Report;
import fit.iuh.cnm_project_be.system.enums.ReportTargetType;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface ReportRepository
        extends BaseRepository<Report, Long> {

    List<Report> findByResolvedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    List<Report> findByResolvedAtIsNotNullOrderByResolvedAtDesc(Pageable pageable);

    List<Report> findByResolvedAtIsNullAndTargetTypeOrderByCreatedAtDesc(ReportTargetType targetType, Pageable pageable);

    long countByResolvedAtIsNull();

    long countByCreatedAtAfter(Instant fromTime);

    long countByTargetTypeAndResolvedAtIsNull(ReportTargetType targetType);
}
