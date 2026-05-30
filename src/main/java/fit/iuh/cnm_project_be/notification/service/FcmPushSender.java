package fit.iuh.cnm_project_be.notification.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import fit.iuh.cnm_project_be.notification.dto.PushNotificationCommand;
import fit.iuh.cnm_project_be.notification.dto.SinglePushSendResult;
import fit.iuh.cnm_project_be.notification.entity.DeviceToken;
import fit.iuh.cnm_project_be.notification.enums.PushProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FcmPushSender implements PushSender {

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenMapper deviceTokenMapper;

    @Override
    public PushProvider provider() {
        return PushProvider.FCM;
    }

    @Override
    public SinglePushSendResult send(DeviceToken token, PushNotificationCommand command) {
        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token.getToken())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build());

            if (command.getTitle() != null && !command.getTitle().isBlank()) {
                messageBuilder.setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(command.getTitle())
                        .setBody(command.getBody())
                        .build());
            }

            Map<String, String> data = PushNotificationService.toStringData(command);
            if (!data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            firebaseMessaging.send(messageBuilder.build());
            return SinglePushSendResult.sent();
        } catch (FirebaseMessagingException ex) {
            MessagingErrorCode errorCode = ex.getMessagingErrorCode();
            boolean invalidToken = errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.INVALID_ARGUMENT;
            log.warn(
                    "FCM push failed userId={} deviceId={} platform={} maskedToken={} errorCode={} message={}",
                    token.getUserId(),
                    token.getDeviceId(),
                    token.getPlatform(),
                    deviceTokenMapper.maskToken(token.getToken()),
                    errorCode,
                    ex.getMessage()
            );
            return SinglePushSendResult.failure(
                    invalidToken,
                    errorCode == null ? null : errorCode.name(),
                    ex.getMessage()
            );
        } catch (Exception ex) {
            log.warn(
                    "FCM push failed userId={} deviceId={} platform={} maskedToken={} message={}",
                    token.getUserId(),
                    token.getDeviceId(),
                    token.getPlatform(),
                    deviceTokenMapper.maskToken(token.getToken()),
                    ex.getMessage()
            );
            return SinglePushSendResult.failure(false, null, ex.getMessage());
        }
    }
}
