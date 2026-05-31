package fit.iuh.cnm_project_be.poll.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CreatePollRequest {

    @NotBlank
    @Size(max = 255)
    private String question;

    @Size(min = 2, max = 20)
    private List<@NotBlank @Size(max = 255) String> options;

    private Boolean multipleChoice;
    private Boolean anonymous;
    private Instant expiresAt;
}
