package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageAttachment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageAttachmentRepository
        extends BaseRepository<MessageAttachment, Long> {

    List<MessageAttachment> findByMessageId(Long messageId);

    List<MessageAttachment> findByMessageIdIn(Collection<Long> messageIds);

    @Query("""
        select coalesce(sum(a.fileSize), 0) from MessageAttachment a
        join Message m on m.id = a.messageId
        where m.senderId = :userId
          and m.deletedAt is null
    """)
    Long sumFileSizeBySenderId(@Param("userId") UUID userId);

    @Query("""
        select a from MessageAttachment a
        join Message m on m.id = a.messageId
        where m.senderId = :userId
          and m.deletedAt is null
          and a.fileSize is not null
        order by a.fileSize desc, a.createdAt desc
    """)
    List<MessageAttachment> findLargestFilesBySenderId(@Param("userId") UUID userId, Pageable pageable);
}
