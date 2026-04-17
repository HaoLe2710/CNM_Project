package fit.iuh.cnm_project_be.room.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends BaseRepository<Conversation, UUID> {

    List<Conversation> findByCreatorIdAndDeletedAtIsNull(UUID creatorId);

    @Query(value = """
            select c.*
            from conversations c
            join conversation_members cm on cm.conversation_id = c.id
            where cm.user_id = :userId
              and c.deleted_at is null
            order by c.created_at desc
            """, nativeQuery = true)
    List<Conversation> findAllByMemberId(@Param("userId") UUID userId);

    @Query(value = """
            select c.*
            from conversations c
            join conversation_members cm1
              on cm1.conversation_id = c.id and cm1.user_id = :userA
            join conversation_members cm2
              on cm2.conversation_id = c.id and cm2.user_id = :userB
            where c.type = 'private'
              and c.deleted_at is null
              and (
                    select count(*)
                    from conversation_members cm
                    where cm.conversation_id = c.id
                  ) = 2
            fetch first 1 row only
            """, nativeQuery = true)
    Optional<Conversation> findPrivateConversationByParticipants(@Param("userA") UUID userA, @Param("userB") UUID userB);
}
