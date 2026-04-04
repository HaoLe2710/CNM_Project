package fit.iuh.cnm_project_be.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateUserProfileRequest {

    @Size(max = 100, message = "Display name must be less than 100 characters")
    private String displayName;

    @Size(max = 100, message = "First name must be less than 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must be less than 100 characters")
    private String lastName;

    @Size(max = 500, message = "Avatar URL must be less than 500 characters")
    private String avatarUrl;

    @Size(max = 500, message = "Bio must be less than 500 characters")
    private String bio;

    @Size(max = 30, message = "Phone must be less than 30 characters")
    private String phone;

    @Size(max = 20, message = "Gender must be less than 20 characters")
    private String gender;

    private LocalDate dob;
}
