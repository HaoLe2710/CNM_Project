package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;


import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository
        extends SoftDeleteRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUsernameAndDeletedAtIsNull(String username);

    Optional<UserProfile> findByInviteLinkAndDeletedAtIsNull(String inviteLink);
}