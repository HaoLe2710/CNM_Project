package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.user.entity.UserSetting;
import fit.iuh.cnm_project_be.user.entity.UserSettingId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSettingRepository
        extends BaseRepository<UserSetting, UserSettingId> {

    List<UserSetting> findByUserId(UUID userId);

    Optional<UserSetting> findByUserIdAndKey(UUID userId, String key);
}
