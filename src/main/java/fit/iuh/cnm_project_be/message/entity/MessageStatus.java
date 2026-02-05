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
    private Long messageId;

    @Id
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private MessageDeliveryStatus status;

    private Instant updatedAt = Instant.now();
}
