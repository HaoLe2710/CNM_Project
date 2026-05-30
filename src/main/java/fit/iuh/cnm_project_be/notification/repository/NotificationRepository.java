package fit.iuh.cnm_project_be.notification.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends BaseRepository<Notification, UUID> {

    @Query("""
            select n from Notification n
            where n.recipientId = :recipientId
              and n.deletedAt is null
              and (:unreadOnly = false or n.readAt is null)
              and (n.expiresAt is null or n.expiresAt > :now)
            order by n.createdAt desc
            """)
    List<Notification> findCurrentPage(
            @Param("recipientId") UUID recipientId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("now") Instant now,
            Pageable pageable);

    @Query("""
            select n from Notification n
            where n.recipientId = :recipientId
              and n.deletedAt is null
              and (:unreadOnly = false or n.readAt is null)
              and (n.expiresAt is null or n.expiresAt > :now)
              and n.createdAt < :cursor
            order by n.createdAt desc
            """)
    List<Notification> findPageBeforeCursor(
            @Param("recipientId") UUID recipientId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("now") Instant now,
            @Param("cursor") Instant cursor,
            Pageable pageable);

    @Query("""
            select count(n) from Notification n
            where n.recipientId = :recipientId
              and n.readAt is null
              and n.deletedAt is null
              and (n.expiresAt is null or n.expiresAt > :now)
            """)
    long countUnread(@Param("recipientId") UUID recipientId, @Param("now") Instant now);

    Optional<Notification> findByIdAndRecipientIdAndDeletedAtIsNull(UUID id, UUID recipientId);

    Optional<Notification> findByDedupKey(String dedupKey);

    @Modifying
    @Query("""
            update Notification n
            set n.readAt = :readAt
            where n.recipientId = :recipientId
              and n.readAt is null
              and n.deletedAt is null
              and (n.expiresAt is null or n.expiresAt > :readAt)
            """)
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
