package fit.iuh.cnm_project_be.poll.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.poll.entity.PollOption;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollOptionRepository extends BaseRepository<PollOption, UUID> {

    List<PollOption> findByPollIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(UUID pollId);

    Optional<PollOption> findByIdAndPollIdAndDeletedAtIsNull(UUID id, UUID pollId);

    long countByPollIdAndDeletedAtIsNull(UUID pollId);
}
