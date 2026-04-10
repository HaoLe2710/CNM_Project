package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO để đổi mật khẩu với changePasswordToken
 * Token được lấy từ endpoint verify-current-password
 * Việc kiểm tra confirm password được thực hiện ở phía Frontend
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfirmPasswordChangeRequest {
    @NotBlank(message = "Change password token cannot be empty")
    String changePasswordToken;

    @NotBlank(message = "New password cannot be empty")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,50}$",
            message = "New password must be 6-50 characters long and include uppercase, lowercase letters, and numbers"
    )
    String newPassword;
}
