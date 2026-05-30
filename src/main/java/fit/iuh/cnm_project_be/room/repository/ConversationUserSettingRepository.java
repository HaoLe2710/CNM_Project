package fit.iuh.cnm_project_be.room.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.room.entity.ConversationUserSetting;
import fit.iuh.cnm_project_be.room.enums.GroupConversationLabel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationUserSettingRepository extends BaseRepository<ConversationUserSetting, Long> {

    Optional<ConversationUserSetting> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    List<ConversationUserSetting> findByUserIdAndConversationIdIn(UUID userId, Collection<UUID> conversationIds);

    List<ConversationUserSetting> findByUserIdAndGroupLabel(UUID userId, GroupConversationLabel groupLabel);
}
