package fit.iuh.cnm_project_be.auth.dto.request;

import fit.iuh.cnm_project_be.auth.enums.OtpType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyOtpRequest {
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email;

    @NotBlank(message = "OTP code is required")
    String otpCode;

    @NotNull(message = "OTP type is required")
    OtpType type; // REGISTER, FORGOT_PASSWORD, etc.
}