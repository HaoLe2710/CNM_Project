package fit.iuh.cnm_project_be.poll.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PollOptionRequest {

    @NotBlank
    @Size(max = 255)
    private String text;
}
