package fit.iuh.cnm_project_be.room.dto;

import fit.iuh.cnm_project_be.room.enums.ConversationBackgroundType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateConversationBackgroundRequest {

    @NotNull(message = "Background type is required")
    private ConversationBackgroundType backgroundType;

    private String backgroundColor;

    private String backgroundImageUrl;
}
