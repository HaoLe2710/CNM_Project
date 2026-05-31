package fit.iuh.cnm_project_be.ringback.dto.request;

import lombok.Data;

@Data
public class UpdateRingbackRequest {
    private Boolean enabled;
    private String toneId;
    private String toneName;
    private String artist;
    private String previewUrl;
}
