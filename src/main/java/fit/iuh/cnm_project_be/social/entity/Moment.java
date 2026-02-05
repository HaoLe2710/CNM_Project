package fit.iuh.cnm_project_be.social.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import fit.iuh.cnm_project_be.social.enums.MediaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moments")
@SQLDelete(sql = "UPDATE moments SET deleted_at = now() WHERE id = ?")
@Getter
@Setter
public class Moment extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    private String caption;
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    private MediaType mediaType;

}
