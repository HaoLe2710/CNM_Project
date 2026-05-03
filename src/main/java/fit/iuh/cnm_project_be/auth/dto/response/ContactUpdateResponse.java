package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ContactUpdateResponse {
    private String email;
    private String phone;
    private Instant updatedAt;
    private String message;
    private boolean reLoginRequired;
}
