package fit.iuh.cnm_project_be.common.repository;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.List;

@NoRepositoryBean
public interface SoftDeleteRepository<T extends BaseEntity, ID extends Serializable>
        extends BaseRepository<T, ID> {

    @Query("""
        select e from #{#entityName} e
        where e.deletedAt is null
    """)
    List<T> findAllActive();
}