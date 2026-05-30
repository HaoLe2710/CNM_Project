package fit.iuh.cnm_project_be.notification.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.notification.entity.DeviceToken;
import fit.iuh.cnm_project_be.notification.enums.DevicePlatform;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends BaseRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByUserIdAndDeviceIdAndPlatform(UUID userId, String deviceId, DevicePlatform platform);

    List<DeviceToken> findByUserIdAndDeviceId(UUID userId, String deviceId);

    @Query("""
            select token from DeviceToken token
            where token.userId = :userId
              and token.enabled = true
              and token.revokedAt is null
            """)
    List<DeviceToken> findActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            select token from DeviceToken token
            where token.userId in :userIds
              and token.enabled = true
              and token.revokedAt is null
            """)
    List<DeviceToken> findActiveByUserIds(@Param("userIds") Collection<UUID> userIds);

    List<DeviceToken> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
