package fit.iuh.cnm_project_be.message.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.message.entity.MessageAttachment;

import java.util.List;

public interface MessageAttachmentRepository
        extends BaseRepository<MessageAttachment, Long> {

    List<MessageAttachment> findByMessageId(Long messageId);
}