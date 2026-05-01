package fit.iuh.cnm_project_be.auth.repository;

import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends BaseRepository<Account, UUID> {
    Optional<Account> findByUsername(String username);
    Optional<Account> findByEmail(String email);

    Optional<Account> findByEmailOrPhone(String email, String phone);
    Optional<Account> findByEmailOrPhoneAndDeletedAtIsNull(String email, String phone);
    Optional<Account> findByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByPhone(String phone);
    boolean existsByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByEmail(String email);
    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailOrPhone(String email, String phone);
    boolean existsByEmailOrPhoneAndDeletedAtIsNull(String email, String phone);
}
