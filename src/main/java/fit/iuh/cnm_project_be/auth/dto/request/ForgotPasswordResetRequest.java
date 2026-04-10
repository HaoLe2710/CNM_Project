package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ForgotPasswordResetRequest {
    @NotBlank(message = "Phone or email cannot be empty")
    String identifier;

    @NotBlank(message = "Reset token cannot be empty")
    String resetToken;

    @NotBlank(message = "New password cannot be empty")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,50}$",
            message = "New password must be 6-50 characters long and include uppercase, lowercase letters, and numbers"
    )
    String newPassword;

    @NotBlank(message = "Confirm password cannot be empty")
    String confirmPassword;
}