package fit.iuh.cnm_project_be.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminModerateUserRequest {
    @NotBlank
    private String action;
    private String reason;
    private Integer banHours;
}

