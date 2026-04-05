package fit.iuh.cnm_project_be.auth.repository;

import fit.iuh.cnm_project_be.auth.entity.RefreshToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
	void deleteByAccountId(UUID accountId);
}
