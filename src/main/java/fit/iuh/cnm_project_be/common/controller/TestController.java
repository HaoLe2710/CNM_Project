package fit.iuh.cnm_project_be.common.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test")
@Validated
public class TestController {

    /**
     * 1️⃣ Test success response
     */
    @GetMapping("/success")
    public ApiResponse<String> success(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        return ApiResponse.ok("Everything works", requestId);
    }

    /**
     * 2️⃣ Test NotFoundException
     */
    @GetMapping("/not-found")
    public void notFound() {
        throw new NotFoundException("Resource not found (test)");
    }

    /**
     * 3️⃣ Test BusinessException
     */
    @GetMapping("/business-error")
    public void businessError() {
        throw new BusinessException("Business rule violated (test)");
    }

    /**
     * 4️⃣ Test validation error (@Valid body)
     */
    @PostMapping("/validate")
    public ApiResponse<String> validate(
            @RequestBody @Validated TestRequest request,
            HttpServletRequest http) {

        String requestId = resolveRequestId(http);
        return ApiResponse.ok("Valid input: " + request.getName(), requestId);
    }

    /**
     * 5️⃣ Test constraint violation (query param)
     */
    @GetMapping("/param")
    public String param(@RequestParam @NotBlank String name) {
        return "Hello " + name;
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return (requestId != null && !requestId.isBlank())
                ? requestId
                : UUID.randomUUID().toString();
    }

    @Data
    static class TestRequest {
        @NotBlank(message = "Name must not be blank")
        private String name;
    }
}