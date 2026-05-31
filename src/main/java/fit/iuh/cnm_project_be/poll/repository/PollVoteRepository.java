package fit.iuh.cnm_project_be.poll.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.poll.entity.PollVote;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollVoteRepository extends BaseRepository<PollVote, UUID> {

    List<PollVote> findByPollId(UUID pollId);

    List<PollVote> findByPollIdAndUserId(UUID pollId, UUID userId);

    Optional<PollVote> findByPollIdAndOptionIdAndUserId(UUID pollId, UUID optionId, UUID userId);

    long countByOptionId(UUID optionId);

    @Modifying
    @Query("delete from PollVote v where v.pollId = :pollId and v.userId = :userId")
    void deleteByPollIdAndUserId(@Param("pollId") UUID pollId, @Param("userId") UUID userId);

    @Modifying
    @Query("delete from PollVote v where v.pollId = :pollId and v.optionId = :optionId and v.userId = :userId")
    void deleteByPollIdAndOptionIdAndUserId(
            @Param("pollId") UUID pollId,
            @Param("optionId") UUID optionId,
            @Param("userId") UUID userId);

    @Modifying
    @Query("delete from PollVote v where v.optionId in :optionIds")
    void deleteByOptionIdIn(@Param("optionIds") Collection<UUID> optionIds);
}
