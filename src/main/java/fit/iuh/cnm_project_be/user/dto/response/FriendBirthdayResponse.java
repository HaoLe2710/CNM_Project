package fit.iuh.cnm_project_be.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class FriendBirthdayResponse {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private LocalDate birthDate;
    private LocalDate nextBirthday;
    private long daysUntil;
    private Integer ageTurning;
}
