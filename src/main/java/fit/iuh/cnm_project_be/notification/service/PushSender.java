package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.PushNotificationCommand;
import fit.iuh.cnm_project_be.notification.dto.SinglePushSendResult;
import fit.iuh.cnm_project_be.notification.entity.DeviceToken;
import fit.iuh.cnm_project_be.notification.enums.PushProvider;

public interface PushSender {
    PushProvider provider();

    SinglePushSendResult send(DeviceToken token, PushNotificationCommand command);
}
