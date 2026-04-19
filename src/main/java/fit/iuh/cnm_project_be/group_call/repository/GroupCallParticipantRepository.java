package fit.iuh.cnm_project_be.group_call.repository;

import fit.iuh.cnm_project_be.group_call.entity.GroupCallParticipant;
import fit.iuh.cnm_project_be.group_call.enums.ParticipantState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupCallParticipantRepository extends JpaRepository<GroupCallParticipant, UUID> {

    Optional<GroupCallParticipant> findByGroupCallIdAndUserId(UUID groupCallId, UUID userId);

    List<GroupCallParticipant> findByGroupCallId(UUID groupCallId);

    List<GroupCallParticipant> findByGroupCallIdAndState(UUID groupCallId, ParticipantState state);
}
