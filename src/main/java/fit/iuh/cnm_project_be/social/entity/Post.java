package fit.iuh.cnm_project_be.social.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
@SQLDelete(sql = "UPDATE posts SET deleted_at = now() WHERE id = ?")
@Getter @Setter
public class Post extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "image_url", nullable = false)
    private String imageUrl;
    private String caption;
}
