package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import org.springframework.data.jpa.repository.Query;
import fit.iuh.cnm_project_be.message.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends SoftDeleteRepository<Message, Long> {
        @Query("""
                        select m from Message m
                        where m.conversationId = :conversationId
                          and m.deletedAt is null
                          and not exists (
                                select 1 from MessageUserState state
                                where state.messageId = m.id
                                  and state.userId = :userId
                                  and (state.hiddenAt is not null or state.deletedForMeAt is not null)
                          )
                        order by m.createdAt desc, m.id desc
                        """)
        List<Message> findVisibleMessages(
                        @Param("conversationId") UUID conversationId,
                        @Param("userId") UUID userId,
                        Pageable pageable);

        @Query("""
                        select m from Message m
                        where m.conversationId = :conversationId
                          and m.deletedAt is null
                          and not exists (
                                select 1 from MessageUserState state
                                where state.messageId = m.id
                                  and state.userId = :userId
                                  and (state.hiddenAt is not null or state.deletedForMeAt is not null)
                          )
                          and (
                                m.createdAt < :cursorCreatedAt


                                or (m.createdAt = :cursorCreatedAt and m.id < :cursorMessageId)
                          )
                        order by m.createdAt desc, m.id desc
                        """)
        List<Message> findVisibleMessagesBeforeCursor(
                        @Param("conversationId") UUID conversationId,
                        @Param("userId") UUID userId,
                        @Param("cursorCreatedAt") java.time.Instant cursorCreatedAt,
                        @Param("cursorMessageId") Long cursorMessageId,
                        Pageable pageable);

        @Query("""
                        select m from Message m
                        where m.conversationId = :conversationId
                          and m.deletedAt is null
                          and not exists (
                                select 1 from MessageUserState state
                                where state.messageId = m.id
                                  and state.userId = :userId
                                  and (state.hiddenAt is not null or state.deletedForMeAt is not null)
                          )
                          and (
                                m.createdAt < :anchorCreatedAt
                                or (m.createdAt = :anchorCreatedAt and m.id < :anchorMessageId)
                          )
                        order by m.createdAt desc, m.id desc
                        """)
        List<Message> findVisibleMessagesOlderThanAnchor(
                        @Param("conversationId") UUID conversationId,
                        @Param("userId") UUID userId,
                        @Param("anchorCreatedAt") java.time.Instant anchorCreatedAt,
                        @Param("anchorMessageId") Long anchorMessageId,
                        Pageable pageable);

        @Query("""
                        select m from Message m
                        where m.conversationId = :conversationId
                          and m.deletedAt is null
                          and not exists (
                                select 1 from MessageUserState state
                                where state.messageId = m.id
                                  and state.userId = :userId
                                  and (state.hiddenAt is not null or state.deletedForMeAt is not null)
                          )
                          and (
                                m.createdAt > :anchorCreatedAt
                                or (m.createdAt = :anchorCreatedAt and m.id > :anchorMessageId)
                          )
                        order by m.createdAt asc, m.id asc
                        """)
        List<Message> findVisibleMessagesNewerThanAnchor(
                        @Param("conversationId") UUID conversationId,
                        @Param("userId") UUID userId,
                        @Param("anchorCreatedAt") java.time.Instant anchorCreatedAt,
                        @Param("anchorMessageId") Long anchorMessageId,
                        Pageable pageable);

        List<Message> findTop50ByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID conversationId);

        List<Message> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID conversationId);

        Optional<Message> findByIdAndDeletedAtIsNull(Long id);
}
