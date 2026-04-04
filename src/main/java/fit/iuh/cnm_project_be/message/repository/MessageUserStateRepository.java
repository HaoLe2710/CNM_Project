package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.message.entity.MessageUserState;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Primary source of truth for per-user message view state:
 * seen, hidden, and deleted-for-me.
 */
public interface MessageUserStateRepository extends BaseRepository<MessageUserState, Long> {

    Optional<MessageUserState> findByMessageIdAndUserId(Long messageId, UUID userId);

    List<MessageUserState> findByMessageIdInAndUserId(List<Long> messageIds, UUID userId);

    default MessageUserState upsert(Long messageId, UUID userId, Consumer<MessageUserState> mutator) {
        MessageUserState state = findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> {
                    MessageUserState newState = new MessageUserState();
                    newState.setMessageId(messageId);
                    newState.setUserId(userId);
                    return newState;
                });

        mutator.accept(state);
        return save(state);
    }

    default MessageUserState markDeletedForMe(Long messageId, UUID userId, Instant deletedForMeAt) {
        return upsert(messageId, userId, state -> {
            if (state.getDeletedForMeAt() == null) {
                state.setDeletedForMeAt(deletedForMeAt);
            }
        });
    }

    @Modifying
    @Transactional
    @Query("""
            update MessageUserState mus
            set mus.seenAt = :seenAt,
                mus.updatedAt = :updatedAt
            where mus.userId = :userId
              and mus.deletedForMeAt is null
              and mus.seenAt is null
              and mus.messageId in (
                    select m.id from Message m
                    where m.conversationId = :conversationId
                      and m.deletedAt is null
              )
            """)
    int markConversationAsSeen(@Param("conversationId") UUID conversationId,
                               @Param("userId") UUID userId,
                               @Param("seenAt") Instant seenAt,
                               @Param("updatedAt") Instant updatedAt);

    @Query("""
            select count(mus) from MessageUserState mus
            join Message m on mus.messageId = m.id
            where m.conversationId = :conversationId
              and m.deletedAt is null
              and m.senderId <> :userId
              and mus.userId = :userId
              and mus.seenAt is null
              and mus.hiddenAt is null
              and mus.deletedForMeAt is null
            """)
    long countUnreadMessages(@Param("conversationId") UUID conversationId,
                             @Param("userId") UUID userId);
}
