package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ResolvedNotificationRecipient {
    private UUID userId;
    private NotificationType type;
    private boolean directMention;
    private boolean replyToRecipientMessage;
    private boolean recipientDirectlyAffected;
}
