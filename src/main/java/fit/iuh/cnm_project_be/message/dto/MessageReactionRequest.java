package fit.iuh.cnm_project_be.message.dto;

import fit.iuh.cnm_project_be.message.enums.MessageReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessageReactionRequest {

    @NotNull
    private MessageReactionType reactionType;
}
