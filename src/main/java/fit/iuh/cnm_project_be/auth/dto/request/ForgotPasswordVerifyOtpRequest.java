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
public class ForgotPasswordVerifyOtpRequest {
    @NotBlank(message = "Phone or email cannot be empty")
    String identifier;

    @NotBlank(message = "OTP cannot be empty")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    String otp;
}