package fit.iuh.cnm_project_be.room.dto;

import fit.iuh.cnm_project_be.room.enums.ConversationType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateConversationRequest {

    private String name;

    @NotNull
    private ConversationType type;

    private List<UUID> participantIds;
}
