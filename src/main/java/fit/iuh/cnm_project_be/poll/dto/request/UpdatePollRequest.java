package fit.iuh.cnm_project_be.poll.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdatePollRequest {

    @Size(max = 255)
    private String question;

    private Boolean multipleChoice;
    private Boolean anonymous;
    private Instant expiresAt;
}
