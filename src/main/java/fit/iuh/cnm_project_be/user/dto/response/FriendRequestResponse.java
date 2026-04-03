package fit.iuh.cnm_project_be.user.dto.response;

import fit.iuh.cnm_project_be.user.enums.FriendRequestStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendRequestResponse {
    Long id;
    FriendRequestStatus status;
    Instant createdAt;
    Instant updatedAt;
    UserSummaryResponse sender;
    UserSummaryResponse receiver;
}
