package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository
        extends SoftDeleteRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUsernameAndDeletedAtIsNull(String username);

    Optional<UserProfile> findByInviteLinkAndDeletedAtIsNull(String inviteLink);
//    @Query("""
//    select u from UserProfile u
//    where u.deletedAt is null
//      and u.userId <> :currentUserId
//      and (
//            lower(coalesce(u.username, '')) like lower(concat('%', :keyword, '%'))
//            or lower(coalesce(u.displayName, '')) like lower(concat('%', :keyword, '%'))
//          )
//    order by coalesce(u.displayName, u.username)
//""")
@Query("""
    select u from UserProfile u
    where u.deletedAt is null
      and u.userId <> :currentUserId
      and (
            lower(coalesce(u.username, '')) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(u.displayName, '')) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(u.firstName, '')) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(u.lastName, '')) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(u.phone, '')) like lower(concat('%', :keyword, '%'))
            or lower(concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, '')))
                like lower(concat('%', :keyword, '%'))
            or lower(concat(coalesce(u.lastName, ''), ' ', coalesce(u.firstName, '')))
                like lower(concat('%', :keyword, '%'))
          )
    order by coalesce(u.displayName, concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, '')), u.username)
""")
    List<UserProfile> searchUsers(@Param("currentUserId") UUID currentUserId,
                                  @Param("keyword") String keyword,
                                  Pageable pageable);
}