package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.PushNotificationCommand;
import fit.iuh.cnm_project_be.notification.dto.PushSendResult;
import fit.iuh.cnm_project_be.notification.dto.SinglePushSendResult;
import fit.iuh.cnm_project_be.notification.entity.DeviceToken;
import fit.iuh.cnm_project_be.notification.enums.DevicePlatform;
import fit.iuh.cnm_project_be.notification.enums.PushProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PushNotificationServiceTest {

    @Test
    void sendToUserNoActiveTokensReturnsZero() {
        FakeDeviceTokenService deviceTokenService = new FakeDeviceTokenService(List.of());
        PushNotificationService service = new PushNotificationService(deviceTokenService, List.of(new FakePushSender()));

        PushSendResult result = service.sendToUser(UUID.randomUUID(), command());

        assertThat(result.getTargetTokenCount()).isZero();
        assertThat(result.getSuccessCount()).isZero();
    }

    @Test
    void sendToUserActiveTokensCallsSender() {
        DeviceToken token = token(UUID.randomUUID(), "device-1", "token-one");
        FakePushSender sender = new FakePushSender();
        PushNotificationService service = new PushNotificationService(
                new FakeDeviceTokenService(List.of(token)),
                List.of(sender)
        );

        PushSendResult result = service.sendToUser(token.getUserId(), command());

        assertThat(sender.sentTokenIds).containsExactly(token.getId());
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    void sendToUsersMultipleUsersSendsAllActiveTokens() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        DeviceToken first = token(firstUserId, "device-1", "token-one");
        DeviceToken second = token(secondUserId, "device-2", "token-two");
        FakePushSender sender = new FakePushSender();
        PushNotificationService service = new PushNotificationService(
                new FakeDeviceTokenService(List.of(first, second)),
                List.of(sender)
        );

        PushSendResult result = service.sendToUsers(List.of(firstUserId, secondUserId), command());

        assertThat(sender.sentTokenIds).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(result.getRequestedUserCount()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);
    }

    @Test
    void providerInvalidTokenDisablesToken() {
        DeviceToken token = token(UUID.randomUUID(), "device-1", "token-one");
        FakeDeviceTokenService deviceTokenService = new FakeDeviceTokenService(List.of(token));
        FakePushSender sender = new FakePushSender();
        sender.result = SinglePushSendResult.failure(true, "UNREGISTERED", "invalid token");
        PushNotificationService service = new PushNotificationService(deviceTokenService, List.of(sender));

        PushSendResult result = service.sendToUser(token.getUserId(), command());

        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getDisabledTokenCount()).isEqualTo(1);
        assertThat(deviceTokenService.disabledTokenIds).containsExactly(token.getId());
    }

    @Test
    void oneTokenFailureDoesNotFailWholeBatch() {
        UUID userId = UUID.randomUUID();
        DeviceToken failing = token(userId, "device-1", "token-one");
        DeviceToken succeeding = token(userId, "device-2", "token-two");
        FakePushSender sender = new FakePushSender() {
            @Override
            public SinglePushSendResult send(DeviceToken token, PushNotificationCommand command) {
                sentTokenIds.add(token.getId());
                if (token.getId().equals(failing.getId())) {
                    return SinglePushSendResult.failure(false, "INTERNAL", "temporary");
                }
                return SinglePushSendResult.sent();
            }
        };
        PushNotificationService service = new PushNotificationService(
                new FakeDeviceTokenService(List.of(failing, succeeding)),
                List.of(sender)
        );

        PushSendResult result = service.sendToUser(userId, command());

        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(sender.sentTokenIds).containsExactly(failing.getId(), succeeding.getId());
    }

    @Test
    void dataPayloadConvertedToString() {
        UUID conversationId = UUID.randomUUID();
        PushNotificationCommand command = PushNotificationCommand.builder()
                .data(Map.of("count", 3, "type", "TEST"))
                .conversationId(conversationId)
                .messageId(10L)
                .build();

        Map<String, String> data = PushNotificationService.toStringData(command);

        assertThat(data).containsEntry("count", "3");
        assertThat(data).containsEntry("type", "TEST");
        assertThat(data).containsEntry("conversationId", conversationId.toString());
        assertThat(data).containsEntry("messageId", "10");
    }

    private PushNotificationCommand command() {
        return PushNotificationCommand.builder()
                .title("Title")
                .body("Body")
                .data(Map.of("type", "TEST"))
                .build();
    }

    private DeviceToken token(UUID userId, String deviceId, String tokenValue) {
        DeviceToken token = new DeviceToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setDeviceId(deviceId);
        token.setPlatform(DevicePlatform.ANDROID);
        token.setProvider(PushProvider.FCM);
        token.setToken(tokenValue);
        token.setEnabled(true);
        return token;
    }

    private static class FakePushSender implements PushSender {
        protected final java.util.ArrayList<UUID> sentTokenIds = new java.util.ArrayList<>();
        private SinglePushSendResult result = SinglePushSendResult.sent();

        @Override
        public PushProvider provider() {
            return PushProvider.FCM;
        }

        @Override
        public SinglePushSendResult send(DeviceToken token, PushNotificationCommand command) {
            sentTokenIds.add(token.getId());
            return result;
        }
    }

    private static class FakeDeviceTokenService extends DeviceTokenService {
        private final List<DeviceToken> tokens;
        private final java.util.ArrayList<UUID> disabledTokenIds = new java.util.ArrayList<>();

        FakeDeviceTokenService(List<DeviceToken> tokens) {
            super(null, null, null);
            this.tokens = tokens;
        }

        @Override
        public List<DeviceToken> findActiveTokensByUserId(UUID userId) {
            return tokens.stream()
                    .filter(token -> token.getUserId().equals(userId))
                    .toList();
        }

        @Override
        public List<DeviceToken> findActiveTokensByUserIds(java.util.Collection<UUID> userIds) {
            return tokens.stream()
                    .filter(token -> userIds.contains(token.getUserId()))
                    .toList();
        }

        @Override
        public void disableTokenAfterFailure(UUID tokenId, String reason) {
            disabledTokenIds.add(tokenId);
        }
    }
}
