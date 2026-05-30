package fit.iuh.cnm_project_be.user.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "friendships")
@SQLDelete(sql = "UPDATE friendships SET deleted_at = now() WHERE id = ?")
@Getter @Setter
public class Friendship extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "friend_id", nullable = false)
    private UUID friendId;

    @Column(name = "is_close_friend", nullable = false)
    private boolean closeFriend = false;

    @Column(name = "close_friend_note")
    private String closeFriendNote;

}
