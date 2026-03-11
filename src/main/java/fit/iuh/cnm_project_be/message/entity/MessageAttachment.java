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

    @Column(name = "message_id", nullable = false)
    private Long messageId;
    @Column(name = "file_url", nullable = false)
    private String fileUrl;
//    @Column(name = "file_type", nullable = false)
    private String fileType;
    private Long fileSize;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
