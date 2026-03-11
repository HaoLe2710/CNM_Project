package fit.iuh.cnm_project_be.social.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "moment_tags")
@Getter @Setter
public class MomentTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "moment_id", nullable = false)
    private UUID momentId;
    @Column(name = "tagged_user_id", nullable = false)
    private UUID taggedUserId;
}
