package fit.iuh.cnm_project_be.user.dto.response;

import fit.iuh.cnm_project_be.user.enums.FriendRelationshipStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSearchResponse {
    UUID userId;
    String username;
    String displayName;
    String avatarUrl;
    FriendRelationshipStatus relationshipStatus;
}
