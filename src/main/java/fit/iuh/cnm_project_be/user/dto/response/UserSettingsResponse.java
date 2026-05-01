package fit.iuh.cnm_project_be.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class UserSettingsResponse {
    private Map<String, Object> settings;
    private Instant updatedAt;
}
