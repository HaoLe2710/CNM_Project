package fit.iuh.cnm_project_be.room.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateConversationMemberNicknameRequest {

    @Size(max = 100, message = "Nickname must be less than 100 characters")
    private String nickname;
}
