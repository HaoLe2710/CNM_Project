package fit.iuh.cnm_project_be.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkConversationDeliveredRequest {
    private Long lastDeliveredMessageId;
}

