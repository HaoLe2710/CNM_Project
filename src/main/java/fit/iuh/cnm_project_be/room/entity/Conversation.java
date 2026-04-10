
package fit.iuh.cnm_project_be.room.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.persistence.ConversationTypeConverter;
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

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "type", nullable = false)
    @Convert(converter = ConversationTypeConverter.class)
    private ConversationType type;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;
}
