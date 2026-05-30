package fit.iuh.cnm_project_be.reminder.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.reminder.entity.ConversationReminderParticipant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationReminderParticipantRepository
        extends BaseRepository<ConversationReminderParticipant, UUID> {

    List<ConversationReminderParticipant> findByReminderId(UUID reminderId);

    List<ConversationReminderParticipant> findByReminderIdIn(Collection<UUID> reminderIds);

    Optional<ConversationReminderParticipant> findByReminderIdAndUserId(UUID reminderId, UUID userId);

    void deleteByReminderId(UUID reminderId);
}
