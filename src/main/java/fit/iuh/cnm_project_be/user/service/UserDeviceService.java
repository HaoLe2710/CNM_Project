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
import java.util.List;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class UserDeviceService {

    UserDeviceRepository userDeviceRepository;

    @Transactional
    public UserDevice saveOrUpdateDevice(UUID userId, String deviceId, Platform platform, String deviceName) {
        // 1. Tìm bản ghi cũ dựa trên (userId, deviceId, platform)
        return userDeviceRepository.findByUserIdAndDeviceIdAndPlatform(userId, deviceId, platform)
                .map(existingDevice -> {
                    // 2. Nếu đã tồn tại: Cập nhật thông tin mới đè lên bản ghi cũ
                    existingDevice.setDeviceName(deviceName);
                    existingDevice.setLastSeenAt(Instant.now());
                    // Không setCreatedAt để giữ nguyên ngày tạo đầu tiên
                    return userDeviceRepository.save(existingDevice);
                })
                .orElseGet(() -> {
                    // 3. Nếu chưa có: Tạo mới hoàn toàn
                    UserDevice newDevice = new UserDevice();
                    newDevice.setUserId(userId);
                    newDevice.setDeviceId(deviceId);
                    newDevice.setPlatform(platform);
                    newDevice.setDeviceName(deviceName);
                    newDevice.setLastSeenAt(Instant.now());
                    newDevice.setCreatedAt(Instant.now());
                    return userDeviceRepository.save(newDevice);
                });
    }

    public boolean isDeviceValid(UUID userId, String deviceId, Platform platform) {
        return userDeviceRepository
                .findByUserIdAndDeviceIdAndPlatform(userId, deviceId, platform)
                .isPresent();
    }

    public UserDevice getUserDevice(UUID userId, String deviceId) {
        return userDeviceRepository
                .findByUserIdAndDeviceId(userId, deviceId)
                .orElse(null);
    }

    public List<UserDevice> getUserDeviceByUserId(UUID userId) {
        return userDeviceRepository.findByUserId(userId);
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
