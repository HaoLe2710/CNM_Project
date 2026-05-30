package fit.iuh.cnm_project_be.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FriendshipSettingResponse {
    private UUID userId;
    private UUID friendId;
    @JsonProperty("isCloseFriend")
    private boolean isCloseFriend;
    private String note;
    private Instant createdAt;
    private Instant updatedAt;
}
