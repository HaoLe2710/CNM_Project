package fit.iuh.cnm_project_be.presence.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BatchPresenceRequest {

    @NotEmpty(message = "userIds is required")
    @Size(max = 200, message = "Batch presence request supports at most 200 userIds")
    private List<@NotNull UUID> userIds;
}

