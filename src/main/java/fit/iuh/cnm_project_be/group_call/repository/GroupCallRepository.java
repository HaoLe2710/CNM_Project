package fit.iuh.cnm_project_be.group_call.repository;

import fit.iuh.cnm_project_be.group_call.entity.GroupCall;
import fit.iuh.cnm_project_be.group_call.enums.GroupCallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupCallRepository extends JpaRepository<GroupCall, UUID> {

    List<GroupCall> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    List<GroupCall> findByConversationIdAndStatusOrderByCreatedAtDesc(UUID conversationId, GroupCallStatus status);
}
