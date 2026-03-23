package fit.iuh.cnm_project_be.call.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static org.apache.kafka.common.requests.DeleteAclsResponse.log;

@Service
public class FCMService {

    @Value("${SFU_URL}")
    private String sfuUrl;

    public void sendCallNotification(String fcmToken, String callerName, 
                                      String callId, String roomId) {
        Message message = Message.builder()
            .setToken(fcmToken)   // Token của người nhận
            .putData("type", "INCOMING_CALL")
            .putData("callId", callId)
            .putData("callerName", callerName)
            .putData("roomId", roomId)
            .putData("sfuUrl", sfuUrl)
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)  // Đánh thức điện thoại
                .build())
            .build();

        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            log.error("Gửi FCM thất bại", e);
        }
    }
}
