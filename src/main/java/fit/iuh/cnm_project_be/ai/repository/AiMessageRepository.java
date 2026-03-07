package fit.iuh.cnm_project_be.ai.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.ai.entity.AiMessage;

import java.util.List;
import java.util.UUID;

public interface AiMessageRepository
        extends BaseRepository<AiMessage, Long> {

    List<AiMessage> findByUserIdOrderByCreatedAtDesc(UUID userId);
}