package fit.iuh.cnm_project_be.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class BlockUserRequest {
    @NotNull
    private UUID blockedUserId;

    private String reason;
}
