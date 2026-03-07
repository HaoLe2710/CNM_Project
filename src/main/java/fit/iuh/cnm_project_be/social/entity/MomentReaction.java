package fit.iuh.cnm_project_be.social.entity;

import fit.iuh.cnm_project_be.social.enums.ReactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moment_reactions")
@Getter @Setter
public class MomentReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID momentId;
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private ReactionType reactionType;

    private Instant createdAt = Instant.now();
}
