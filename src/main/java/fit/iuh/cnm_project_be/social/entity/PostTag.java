package fit.iuh.cnm_project_be.social.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_tags")
@Getter
@Setter
public class PostTag {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "tagged_user_id", nullable = false)
    private UUID taggedUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
