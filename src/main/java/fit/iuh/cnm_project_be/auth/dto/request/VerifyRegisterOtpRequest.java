package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.Email;
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
public class VerifyRegisterOtpRequest {
    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email is invalid")
    String email;

    @NotBlank(message = "OTP cannot be empty")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    String otp;
}