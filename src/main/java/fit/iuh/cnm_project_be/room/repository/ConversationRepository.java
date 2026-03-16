package fit.iuh.cnm_project_be.room.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends BaseRepository<Conversation, UUID> {

    List<Conversation> findByCreatorIdAndDeletedAtIsNull(UUID creatorId);

    List<Conversation> findAllByMemberId(UUID userId);
}