package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogoutDeviceRequest {
    
    @NotBlank(message = "deviceId cannot be blank")
    String deviceId;
    
    @NotBlank(message = "platform cannot be blank")
    String platform;
}
