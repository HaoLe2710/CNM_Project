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
    Optional<Account> findByUsernameOrEmail(String username, String email);
    boolean existsAccountByUsername(String username);
    boolean existsAccountByEmail(String email);
    Optional<Account> findByUserIdAndDeletedAtIsNull(UUID userId);
}
