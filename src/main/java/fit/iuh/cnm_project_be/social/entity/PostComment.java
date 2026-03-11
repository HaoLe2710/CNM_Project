package fit.iuh.cnm_project_be.social.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_comments")
@SQLDelete(sql = "UPDATE post_comments SET deleted_at = now() WHERE id = ?")
@Getter @Setter
public class PostComment extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(columnDefinition = "text", nullable = false )
    private String content;
}
