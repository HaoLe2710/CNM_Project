package fit.iuh.cnm_project_be.ai.repository;

import fit.iuh.cnm_project_be.ai.dto.MessageDto;
import fit.iuh.cnm_project_be.ai.entity.AiMessage;
import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AiMessageRepository extends BaseRepository<AiMessage, Long> {


    @Query("""
    SELECT COUNT(ms)
    FROM Message m 
    JOIN MessageStatus ms ON m.id = ms.messageId
    WHERE m.conversationId = :conversationId 
      AND ms.userId = :userId 
      AND UPPER(ms.status) = 'DELIVERED'
""")
    int countUnreadMessages(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId
    );


    @Query("""
            SELECT new fit.iuh.cnm_project_be.ai.dto.MessageDto(
                m.id, m.conversationId, up.displayName, m.content, m.createdAt
            )
            FROM Message m join MessageStatus ms ON m.id = ms.messageId join UserProfile up on up.userId = m.senderId
            WHERE m.conversationId = :conversationId and ms.userId = :userId and UPPER(ms.status)='DELIVERED'
            ORDER BY m.createdAt DESC
            limit :limit
""")
    List<MessageDto> findRecentUnreadMessages(
            @Param("conversationId") UUID conversationId,
            @Param("userId")UUID userId,
            @Param("limit")int limit
    );



    @Query("""
            SELECT new fit.iuh.cnm_project_be.ai.dto.MessageDto(
                m.id, m.conversationId, up.displayName, m.content, m.createdAt
            )
            FROM Message m join MessageStatus ms ON m.id = ms.messageId join UserProfile up on up.userId = m.senderId
            WHERE m.conversationId = :conversationId and ms.userId = :userId and UPPER(ms.status)='DELIVERED'
            ORDER BY m.createdAt DESC
            limit 1
""")
    MessageDto findFirstByConversationIdOrderByCreatedAtDesc(@Param("conversationId") UUID conversationId,@Param("userId") UUID userId);
}