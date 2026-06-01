package fit.iuh.cnm_project_be.call.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FCMService {

    // @Value("${sfu.url}")
    @Value("${app.sfu.url:${SFU_URL:${APP_SFU_URL:http://localhost:4443}}}")
    private String sfuUrl;

    public void sendCallNotification(String fcmToken, String callerName,
            String callId, String roomId) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .putData("type", "INCOMING_CALL")
                .putData("callId", callId)
                .putData("callerName", callerName)
                .putData("roomId", roomId)
                .putData("sfuUrl", sfuUrl)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .build();

        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            log.error("Gửi FCM thất bại (Token có thể không hợp lệ): {}", e.getMessage());
        }
    }
}
