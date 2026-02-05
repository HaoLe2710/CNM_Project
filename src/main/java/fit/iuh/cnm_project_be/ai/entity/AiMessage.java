package fit.iuh.cnm_project_be.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_messages")
@Getter @Setter
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID userId;

    private String role;

    @Column(columnDefinition = "text")
    private String content;

    private Instant createdAt = Instant.now();
}
