package fit.iuh.cnm_project_be.social.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.util.UUID;

@Entity
@Table(name = "moment_comments")
@SQLDelete(sql = "UPDATE moment_comments SET deleted_at = now() WHERE id = ?")
@Getter
@Setter
public class MomentComment extends BaseEntity {

    @Id
    private UUID id;

    @Column(name = "moment_id", nullable = false)
    private UUID momentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;
}
