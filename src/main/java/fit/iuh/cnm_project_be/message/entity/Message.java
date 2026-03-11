package fit.iuh.cnm_project_be.message.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import fit.iuh.cnm_project_be.message.enums.MessageType;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    private Long replyTo;

}
