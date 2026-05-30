package fit.iuh.cnm_project_be.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NotificationContent {
    private String title;
    private String body;
    private Map<String, Object> metadata;
    private Map<String, Object> pushData;
}
