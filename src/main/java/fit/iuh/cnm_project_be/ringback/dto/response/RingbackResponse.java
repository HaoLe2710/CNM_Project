package fit.iuh.cnm_project_be.ringback.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RingbackResponse {
    private boolean enabled;
    private String toneId;
    private String toneName;
    private String artist;
    private String previewUrl;
}
