package fit.iuh.cnm_project_be.room.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddConversationMemberRequest {

    @NotNull
    private UUID userId;
}
