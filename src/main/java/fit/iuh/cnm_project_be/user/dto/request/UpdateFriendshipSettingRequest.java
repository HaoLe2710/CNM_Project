package fit.iuh.cnm_project_be.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFriendshipSettingRequest {
    @JsonProperty("isCloseFriend")
    private Boolean isCloseFriend;

    @Size(max = 255, message = "closeFriendNote must not exceed 255 characters")
    private String note;
}
