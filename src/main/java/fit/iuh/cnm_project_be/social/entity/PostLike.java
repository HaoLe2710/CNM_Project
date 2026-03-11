package fit.iuh.cnm_project_be.social.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_likes")
@Getter @Setter
public class PostLike {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private Instant createdAt = Instant.now();
}
