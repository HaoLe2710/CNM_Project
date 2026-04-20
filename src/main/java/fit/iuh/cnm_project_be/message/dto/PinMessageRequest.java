package fit.iuh.cnm_project_be.message.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PinMessageRequest {
    @NotNull
    private Boolean pinned;
}
