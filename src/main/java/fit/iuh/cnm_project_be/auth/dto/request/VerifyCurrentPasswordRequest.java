package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO để xác thực mật khẩu hiện tại
 * Sử dụng trước khi đổi mật khẩu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VerifyCurrentPasswordRequest {
    @NotBlank(message = "Current password cannot be empty")
    String currentPassword;
}
