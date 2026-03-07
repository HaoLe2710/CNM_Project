package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.user.entity.UserDevice;

import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository
        extends BaseRepository<UserDevice, Long> {

    Optional<UserDevice> findByUserId(UUID userId);

    Optional<UserDevice> findByDeviceId(String deviceId);
}