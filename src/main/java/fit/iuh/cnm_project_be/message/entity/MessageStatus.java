package fit.iuh.cnm_project_be.message.entity;

import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_status")
@IdClass(MessageStatusId.class)
@Getter
@Setter
public class MessageStatus {

    @Id
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MessageDeliveryStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
