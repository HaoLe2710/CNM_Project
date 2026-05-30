package fit.iuh.cnm_project_be.message.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberReadStateId implements Serializable {
    private UUID conversationId;
    private UUID userId;
}
