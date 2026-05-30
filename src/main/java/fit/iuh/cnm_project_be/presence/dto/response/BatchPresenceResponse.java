package fit.iuh.cnm_project_be.presence.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchPresenceResponse {
    private List<PresenceItemResponse> items;
}

