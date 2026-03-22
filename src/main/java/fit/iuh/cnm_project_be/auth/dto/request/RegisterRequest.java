package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
	@NotBlank(message = "Username cannot be empty")
	@Size(min = 3, max = 50, message = "Username must be from 3 to 50 characters")
	String username;

	@NotBlank(message = "Password can not be empty")
	@Pattern(
			regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{6,50}$",
			message = "Password must be 6-50 characters long and include uppercase, lowercase letters, and numbers"
	)
	String password;
}
