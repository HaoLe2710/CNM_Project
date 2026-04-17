package fit.iuh.cnm_project_be.room.dto;

import fit.iuh.cnm_project_be.room.enums.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberResponse {
    private UUID userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private MemberRole role;
}
