package fit.iuh.cnm_project_be.room.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ConversationMemberRoleRequest {

    @NotNull
    private UUID userId;
}
