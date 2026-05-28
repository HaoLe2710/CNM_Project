package fit.iuh.cnm_project_be.social.dto.request;

import fit.iuh.cnm_project_be.social.enums.MediaType;
import fit.iuh.cnm_project_be.social.enums.MomentAudioMode;
import fit.iuh.cnm_project_be.social.enums.MomentVisibilityMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMomentRequest {

    @NotBlank(message = "Media URL is required")
    private String mediaUrl;

    @NotNull(message = "Media type is required")
    private MediaType mediaType;

    private String caption;

    private String coverUrl;

    private Integer durationSeconds;

    private MomentVisibilityMode visibilityMode;

    private MomentAudioMode audioMode;

    private String musicTrackId;

    private String musicTitle;

    private String musicArtist;

    private String musicUrl;

    private Integer musicStartSeconds;
}
