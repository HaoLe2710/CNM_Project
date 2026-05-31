package fit.iuh.cnm_project_be.message.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class MessageMentionPayload {

    @NotNull
    private UUID userId;

    private String displayName;
}
