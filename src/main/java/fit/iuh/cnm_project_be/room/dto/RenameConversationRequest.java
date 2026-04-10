package fit.iuh.cnm_project_be.room.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RenameConversationRequest {

    @NotNull
    @Size(max = 100, message = "Conversation name must be less than 100 characters")
    private String name;
}
