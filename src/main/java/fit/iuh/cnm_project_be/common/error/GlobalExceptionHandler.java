package fit.iuh.cnm_project_be.common.error;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return (requestId != null && !requestId.isBlank())
                ? requestId
                : UUID.randomUUID().toString();
    }

    /**
     * Handle business & domain exceptions
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(
            ApiException ex,
            HttpServletRequest request) {

        String requestId = resolveRequestId(request);
        ErrorCode code = ex.getErrorCode();

        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(
                        code.name(),
                        ex.getMessage(),
                        null,
                        requestId
                ));
    }

    /**
     * Handle @Valid body errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationError(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String requestId = resolveRequestId(request);

        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "reason", fieldError.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(
                        ErrorCode.VALIDATION_ERROR.name(),
                        ErrorCode.VALIDATION_ERROR.defaultMessage(),
                        errors,
                        requestId
                ));
    }

    /**
     * Handle constraint validation (query param, path param)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        String requestId = resolveRequestId(request);

        var errors = ex.getConstraintViolations()
                .stream()
                .map(v -> Map.of(
                        "field", v.getPropertyPath().toString(),
                        "reason", v.getMessage()
                ))
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(
                        ErrorCode.VALIDATION_ERROR.name(),
                        ErrorCode.VALIDATION_ERROR.defaultMessage(),
                        errors,
                        requestId
                ));
    }

    /**
     * Catch-all fallback
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(
            Exception ex,
            HttpServletRequest request) {

        String requestId = resolveRequestId(request);
        ex.printStackTrace();
        // TODO: log.error("Unexpected error", ex);

        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.fail(
                        ErrorCode.INTERNAL_ERROR.name(),
                        ErrorCode.INTERNAL_ERROR.defaultMessage(),
                        null,
                        requestId
                ));
    }
}