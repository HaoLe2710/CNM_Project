package fit.iuh.cnm_project_be.cloud.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SendCloudFileToConversationRequest {

    @NotNull(message = "conversationId is required")
    private UUID conversationId;

    private String message;
}
