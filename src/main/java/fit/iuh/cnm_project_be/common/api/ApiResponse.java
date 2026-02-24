package fit.iuh.cnm_project_be.common.api;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Object errors,
        Meta meta
) {

    public record Meta(String requestId, Instant timestamp) {}

    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>(
                true,
                "OK",
                "OK",
                data,
                null,
                new Meta(requestId, Instant.now())
        );
    }

    public static ApiResponse<Void> fail(
            String code,
            String message,
            Object errors,
            String requestId) {

        return new ApiResponse<>(
                false,
                code,
                message,
                null,
                errors,
                new Meta(requestId, Instant.now())
        );
    }
}