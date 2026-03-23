package fit.iuh.cnm_project_be.message.dto;

import fit.iuh.cnm_project_be.message.enums.MessageReactionType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageReactionSummary {

    private MessageReactionType type;
    private long count;
}
