package fit.iuh.cnm_project_be.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NotificationPageResponse {
    private List<NotificationResponse> items;
    private String nextCursor;
    private boolean hasMore;
}
