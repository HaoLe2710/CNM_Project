package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Response DTO khi xác thực mật khẩu hiện tại thành công
 * Chứa changePasswordToken để sử dụng trong bước đổi mật khẩu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VerifyPasswordResponse {
    String changePasswordToken;
    String message;
    Long expiresIn; // Thời gian hết hạn token tính bằng giây
}
