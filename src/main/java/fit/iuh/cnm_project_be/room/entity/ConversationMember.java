package fit.iuh.cnm_project_be.room.entity;

import fit.iuh.cnm_project_be.room.enums.MemberRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_members")
@IdClass(ConversationMemberId.class)
@Getter
@Setter
public class ConversationMember {

    @Id
    private UUID conversationId;

    @Id
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    private Instant joinedAt = Instant.now();
}
