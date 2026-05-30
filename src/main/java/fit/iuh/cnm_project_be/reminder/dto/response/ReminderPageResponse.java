package fit.iuh.cnm_project_be.reminder.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReminderPageResponse {
    private List<ConversationReminderResponse> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private boolean hasMore;
}
