package fit.iuh.cnm_project_be.user.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileResponse {
    UUID userId;
    String username;
    String displayName;
    String firstName;
    String lastName;
    String avatarUrl;
    String inviteLink;
    String qrCodeUrl;
    String bio;
    String phone;
    Instant createdAt;
    Instant updatedAt;
}
