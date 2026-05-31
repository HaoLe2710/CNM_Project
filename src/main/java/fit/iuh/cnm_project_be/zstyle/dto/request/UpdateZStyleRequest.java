package fit.iuh.cnm_project_be.zstyle.dto.request;

import lombok.Data;

@Data
public class UpdateZStyleRequest {
    private Boolean enabled;
    private String themeId;
    private String themeName;
    private String backgroundUrl;
    private String accentColor;
}
