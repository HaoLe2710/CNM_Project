package fit.iuh.cnm_project_be.message.entity;

import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.persistence.MessageTypeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "message_attachments")
@Getter @Setter
public class MessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;
    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "original_file_name")
    private String originalFileName;

    private String fileType;

    @Column(name = "attachment_type", nullable = false)
    @Convert(converter = MessageTypeConverter.class)
    private MessageType attachmentType;

    private Long fileSize;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
