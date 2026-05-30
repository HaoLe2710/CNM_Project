package fit.iuh.cnm_project_be.notification.dto;

public record SinglePushSendResult(
        boolean success,
        boolean invalidToken,
        String providerErrorCode,
        String providerErrorMessage
) {
    public static SinglePushSendResult sent() {
        return new SinglePushSendResult(true, false, null, null);
    }

    public static SinglePushSendResult failure(boolean invalidToken, String providerErrorCode, String providerErrorMessage) {
        return new SinglePushSendResult(false, invalidToken, providerErrorCode, providerErrorMessage);
    }
}
