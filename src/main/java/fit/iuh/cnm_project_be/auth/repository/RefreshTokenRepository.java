package fit.iuh.cnm_project_be.auth.repository;

import fit.iuh.cnm_project_be.auth.entity.RefreshToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
	void deleteByAccountId(long accountId);
}
