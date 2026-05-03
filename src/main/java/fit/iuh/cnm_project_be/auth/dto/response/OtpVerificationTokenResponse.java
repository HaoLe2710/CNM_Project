package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OtpVerificationTokenResponse {
    private String token;
    private String destination;
    private long expiresInSeconds;
}
