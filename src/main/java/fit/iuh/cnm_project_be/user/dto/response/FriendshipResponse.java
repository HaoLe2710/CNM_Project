package fit.iuh.cnm_project_be.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendshipResponse {
    UUID friendshipId;
    Instant createdAt;
    @JsonProperty("isCloseFriend")
    boolean isCloseFriend;
    String closeFriendNote;
    UserSummaryResponse friend;
}
