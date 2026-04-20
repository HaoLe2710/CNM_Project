package fit.iuh.cnm_project_be.message.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MessageContextResponse {

    private Long anchorMessageId;
    private List<MessageResponse> items;
    private boolean hasOlder;
    private boolean hasNewer;
    private Long latestMessageId;
    private String latestCursor;
}
