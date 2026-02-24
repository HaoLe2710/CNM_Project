package fit.iuh.cnm_project_be.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    OK(HttpStatus.OK, "OK"),

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid request"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    BUSINESS_ERROR(HttpStatus.BAD_REQUEST, "Business rule violated"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}