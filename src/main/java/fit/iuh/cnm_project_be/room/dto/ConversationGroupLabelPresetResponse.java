package fit.iuh.cnm_project_be.room.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationGroupLabelPresetResponse {
    private String code;
    private String displayName;
    private String color;
}
