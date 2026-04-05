package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.user.entity.UserDevice;
import fit.iuh.cnm_project_be.user.enums.Platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository
        extends BaseRepository<UserDevice, Long> {

    List<UserDevice> findByUserId(UUID userId);

    Optional<UserDevice> findByDeviceId(String deviceId);

    Optional<UserDevice> findByUserIdAndPlatform(UUID userId, Platform platform);

    Optional<UserDevice> findByUserIdAndDeviceIdAndPlatform(UUID userId, String deviceId, Platform platform);

    void deleteByUserIdAndDeviceIdAndPlatform(UUID userId, String deviceId, Platform platform);
}