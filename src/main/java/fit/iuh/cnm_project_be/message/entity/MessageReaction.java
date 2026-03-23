package fit.iuh.cnm_project_be.message.entity;

import fit.iuh.cnm_project_be.message.enums.MessageReactionType;
import fit.iuh.cnm_project_be.message.persistence.MessageReactionTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_reactions")
@Getter
@Setter
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reaction_type", nullable = false)
    @Convert(converter = MessageReactionTypeConverter.class)
    private MessageReactionType reactionType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
