package fit.iuh.cnm_project_be.reminder.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.reminder.entity.ConversationReminder;
import fit.iuh.cnm_project_be.reminder.enums.ReminderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationReminderRepository extends BaseRepository<ConversationReminder, UUID> {

    Optional<ConversationReminder> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select r
            from ConversationReminder r
            where r.conversationId = :conversationId
              and r.deletedAt is null
              and r.status = coalesce(:status, r.status)
              and r.remindAt >= coalesce(:fromTime, r.remindAt)
              and r.remindAt <= coalesce(:toTime, r.remindAt)
            order by r.remindAt asc, r.createdAt desc
            """)
    Page<ConversationReminder> findConversationReminders(
            @Param("conversationId") UUID conversationId,
            @Param("status") ReminderStatus status,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);

    @Query("""
            select distinct r
            from ConversationReminder r
            join ConversationReminderParticipant p on p.reminderId = r.id
            where p.userId = :userId
              and r.deletedAt is null
              and exists (
                    select 1
                    from ConversationMember cm
                    where cm.conversationId = r.conversationId
                      and cm.userId = :userId
              )
              and r.status = coalesce(:status, r.status)
              and r.remindAt >= coalesce(:fromTime, r.remindAt)
              and r.remindAt <= coalesce(:toTime, r.remindAt)
            order by r.remindAt asc, r.createdAt desc
            """)
    Page<ConversationReminder> findParticipantReminders(
            @Param("userId") UUID userId,
            @Param("status") ReminderStatus status,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);

    @Query("""
            select r
            from ConversationReminder r
            where r.status = :status
              and r.remindAt <= :now
              and r.dueNotifiedAt is null
              and r.deletedAt is null
            order by r.remindAt asc
            """)
    List<ConversationReminder> findDueReminders(
            @Param("status") ReminderStatus status,
            @Param("now") Instant now,
            Pageable pageable);
}
