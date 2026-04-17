package fit.iuh.cnm_project_be.room.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateConversationAvatarRequest {

    @NotNull
    @Size(max = 500, message = "Avatar URL must be less than 500 characters")
    private String avatarUrl;
}
