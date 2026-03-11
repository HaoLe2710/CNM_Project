
package fit.iuh.cnm_project_be.room.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@SQLDelete(sql = "UPDATE conversations SET deleted_at = now() WHERE id = ?")
@Getter
@Setter
public class Conversation extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ConversationType type;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;
}
