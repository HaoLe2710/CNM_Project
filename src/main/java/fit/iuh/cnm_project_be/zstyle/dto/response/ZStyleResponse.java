package fit.iuh.cnm_project_be.zstyle.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZStyleResponse {
    private boolean enabled;
    private String themeId;
    private String themeName;
    private String backgroundUrl;
    private String accentColor;
}
