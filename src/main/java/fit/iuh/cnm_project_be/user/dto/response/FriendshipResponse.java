package fit.iuh.cnm_project_be.user.dto.response;

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
    UserSummaryResponse friend;
}
