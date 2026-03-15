package fit.iuh.cnm_project_be.message.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class TypingRequest {
    private UUID userId;
    private boolean isTyping;
}