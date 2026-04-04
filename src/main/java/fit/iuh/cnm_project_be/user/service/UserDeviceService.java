package fit.iuh.cnm_project_be.user.service;

import fit.iuh.cnm_project_be.user.entity.UserDevice;
import fit.iuh.cnm_project_be.user.enums.Platform;
import fit.iuh.cnm_project_be.user.repository.UserDeviceRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class UserDeviceService {

    UserDeviceRepository userDeviceRepository;

    @Transactional
    public UserDevice saveOrUpdateDevice(UUID userId, String deviceId, Platform platform, String deviceName) {
        // Tìm device cũ của user với platform này
        UserDevice existingDevice = userDeviceRepository
                .findByUserIdAndPlatform(userId, platform)
                .orElse(null);

        if (existingDevice != null) {
            // Nếu cùng deviceId → chỉ update lastSeenAt
            if (existingDevice.getDeviceId().equals(deviceId)) {
                existingDevice.setLastSeenAt(Instant.now());
                return userDeviceRepository.save(existingDevice);
            }
            // Nếu khác deviceId → thay thế device cũ
            userDeviceRepository.delete(existingDevice);
        }

        // Tạo device mới
        UserDevice newDevice = new UserDevice();
        newDevice.setUserId(userId);
        newDevice.setDeviceId(deviceId);
        newDevice.setPlatform(platform);
        newDevice.setDeviceName(deviceName);
        newDevice.setLastSeenAt(Instant.now());
        newDevice.setCreatedAt(Instant.now());

        return userDeviceRepository.save(newDevice);
    }

    public boolean isDeviceValid(UUID userId, String deviceId, Platform platform) {
        return userDeviceRepository
                .findByUserIdAndDeviceIdAndPlatform(userId, deviceId, platform)
                .isPresent();
    }

    @Transactional
    public void deleteDevice(UUID userId, String deviceId, String platformStr) {
        try {
            Platform platform = Platform.valueOf(platformStr.toUpperCase());
            userDeviceRepository.deleteByUserIdAndDeviceIdAndPlatform(userId, deviceId, platform);
        } catch (IllegalArgumentException e) {
            // Platform không hợp lệ, bỏ qua
        }
    }
}
