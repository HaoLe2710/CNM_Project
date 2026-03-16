package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.message.entity.MessageStatus;
import fit.iuh.cnm_project_be.message.entity.MessageStatusId;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageStatusRepository
        extends BaseRepository<MessageStatus, MessageStatusId> {

    List<MessageStatus> findByUserId(UUID userId);

    Optional<MessageStatus> findByMessageIdAndUserId(Long messageId, UUID userId);

    @Query("SELECT COUNT(ms) FROM MessageStatus ms " +
            "JOIN Message m ON ms.messageId = m.id " +
            "WHERE m.conversationId = :conversationId " +
            "AND ms.userId = :userId " +
            "AND ms.status != 'SEEN' " +
            "AND m.deletedAt IS NULL")
    long countUnreadMessages(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE MessageStatus ms SET ms.status = 'SEEN' " +
            "WHERE ms.userId = :userId " +
            "AND ms.status != 'SEEN' " +
            "AND ms.messageId IN (SELECT m.id FROM Message m WHERE m.conversationId = :conversationId)")
    void markAllAsSeen(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);
}