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
public class RegisterRequest {
	@NotBlank(message = "Phone cannot be empty")
	@Pattern(
			regexp = "^0\\d{9}$",
			message = "Phone must be 10 digits starting with 0"
	)
	String username;

	@NotBlank(message = "Password can not be empty")
	@Pattern(
			regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,50}$",
			message = "Password must be 6-50 characters long and include uppercase, lowercase letters, and numbers"
	)
	String password;
}
