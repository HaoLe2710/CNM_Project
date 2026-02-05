package fit.iuh.cnm_project_be.message.entity;

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

    private Long messageId;

    private String fileUrl;
    private String fileType;
    private Long fileSize;

    private Instant createdAt = Instant.now();
}
