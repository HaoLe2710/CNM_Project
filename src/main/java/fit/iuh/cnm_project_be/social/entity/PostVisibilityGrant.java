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
@Table(name = "post_visibility_grants")
@Getter
@Setter
public class PostVisibilityGrant {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "viewer_user_id", nullable = false)
    private UUID viewerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
