package fit.iuh.cnm_project_be.user.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserBlockResponse {
    Long id;
    String reason;
    Instant createdAt;
    UserSummaryResponse blockedUser;
}
