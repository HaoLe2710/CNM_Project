package fit.iuh.cnm_project_be.user.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateUserSettingsRequest {
    @NotEmpty(message = "Settings payload is required")
    private Map<String, Object> settings;
}
