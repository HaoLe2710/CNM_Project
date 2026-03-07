package fit.iuh.cnm_project_be.room.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.room.entity.Conversation;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository
        extends SoftDeleteRepository<Conversation, UUID> {

    List<Conversation> findByCreatorIdAndDeletedAtIsNull(UUID creatorId);
}