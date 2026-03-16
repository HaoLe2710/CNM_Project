package fit.iuh.cnm_project_be.room.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationStatusPayload {
    private UUID conversationId;
    private String status;
}