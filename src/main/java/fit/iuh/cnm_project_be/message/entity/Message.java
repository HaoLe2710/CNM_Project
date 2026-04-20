package fit.iuh.cnm_project_be.message.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.persistence.MessageTypeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@SQLDelete(sql = "UPDATE messages SET deleted_at = now() WHERE id = ?")
public class Message extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "original_link_url", columnDefinition = "text")
    private String originalLinkUrl;

    @Column(name = "message_type", nullable = false)
    @Convert(converter = MessageTypeConverter.class)
    private MessageType messageType;

    @Column(name = "reply_to")
    private Long replyToMessageId;

    @Column(name = "reply_to_sender_id")
    private UUID replyToSenderId;

    @Column(name = "reply_to_content_preview", columnDefinition = "text")
    private String replyToContentPreview;

    @Column(name = "reply_to_type")
    @Convert(converter = MessageTypeConverter.class)
    private MessageType replyToType;

    @Column(name = "edited_at")
    private Instant editedAt;

}
