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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class UserDeviceService {

    UserDeviceRepository userDeviceRepository;

    private static final List<Platform> MOBILE_PLATFORMS = List.of(Platform.ANDROID, Platform.IOS);

    @Transactional
    public UserDevice saveOrUpdateDevice(UUID userId, String deviceId, Platform platform, String deviceName) {
        Instant now = Instant.now();

        UserDevice savedDevice = userDeviceRepository.findByUserIdAndDeviceIdAndPlatform(userId, deviceId, platform)
                .map(existingDevice -> updateDevice(existingDevice, deviceId, platform, deviceName, now))
                .orElseGet(() -> userDeviceRepository.findByUserIdAndPlatform(userId, platform)
                        .map(existingDevice -> updateDevice(existingDevice, deviceId, platform, deviceName, now))
                        .orElseGet(() -> findExistingSlot(userId, platform)
                        .map(existingDevice -> updateDevice(existingDevice, deviceId, platform, deviceName, now))
                        .orElseGet(() -> createDevice(userId, deviceId, platform, deviceName, now))));

        cleanupDuplicateDevicesInSameSlot(userId, savedDevice);
        return savedDevice;
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
        } catch (IllegalArgumentException ignored) {
            // Ignore invalid platform values.
        }
    }

    private UserDevice updateDevice(
            UserDevice device,
            String deviceId,
            Platform platform,
            String deviceName,
            Instant now
    ) {
        device.setDeviceId(deviceId);
        device.setPlatform(platform);
        device.setDeviceName(deviceName);
        device.setLastSeenAt(now);
        return userDeviceRepository.save(device);
    }

    private UserDevice createDevice(
            UUID userId,
            String deviceId,
            Platform platform,
            String deviceName,
            Instant now
    ) {
        UserDevice newDevice = new UserDevice();
        newDevice.setUserId(userId);
        newDevice.setDeviceId(deviceId);
        newDevice.setPlatform(platform);
        newDevice.setDeviceName(deviceName);
        newDevice.setLastSeenAt(now);
        newDevice.setCreatedAt(now);
        return userDeviceRepository.save(newDevice);
    }

    private java.util.Optional<UserDevice> findExistingSlot(UUID userId, Platform platform) {
        if (platform == Platform.WEB) {
            return userDeviceRepository.findByUserIdAndPlatform(userId, Platform.WEB);
        }

        return userDeviceRepository.findByUserIdAndPlatformIn(userId, MOBILE_PLATFORMS)
                .stream()
                .sorted(Comparator.comparing(
                        UserDevice::getLastSeenAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }

    private void cleanupDuplicateDevicesInSameSlot(UUID userId, UserDevice currentDevice) {
        List<UserDevice> sameSlotDevices = currentDevice.getPlatform() == Platform.WEB
                ? userDeviceRepository.findByUserIdAndPlatform(userId, Platform.WEB).stream().toList()
                : new ArrayList<>(userDeviceRepository.findByUserIdAndPlatformIn(userId, MOBILE_PLATFORMS));

        sameSlotDevices.stream()
                .filter(device -> !device.getId().equals(currentDevice.getId()))
                .forEach(userDeviceRepository::delete);
    }
}
