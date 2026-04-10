package fit.iuh.cnm_project_be.room.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MuteConversationRequest {
    @NotNull
    private Boolean muted;
}
