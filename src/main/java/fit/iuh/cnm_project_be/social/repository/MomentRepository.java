package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.social.entity.Moment;
import fit.iuh.cnm_project_be.social.enums.MediaType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MomentRepository
        extends SoftDeleteRepository<Moment, UUID> {

    List<Moment> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    @Query("""
        select m from Moment m
        where m.deletedAt is null
          and m.mediaType = :mediaType
          and m.userId in :userIds
        order by m.createdAt desc
    """)
    List<Moment> findByUserIdsAndMediaType(
            @Param("userIds") Collection<UUID> userIds,
            @Param("mediaType") MediaType mediaType,
            Pageable pageable);

    @Query("""
        select m from Moment m
        where m.deletedAt is null
          and m.mediaType = :mediaType
          and m.id not in :excludedIds
        order by m.createdAt desc
    """)
    List<Moment> findByMediaTypeExcludingIds(
            @Param("mediaType") MediaType mediaType,
            @Param("excludedIds") Collection<UUID> excludedIds,
            Pageable pageable);

    @Query("""
        select m from Moment m
        where m.deletedAt is null
          and m.mediaType = :mediaType
        order by m.createdAt desc
    """)
    List<Moment> findByMediaType(@Param("mediaType") MediaType mediaType, Pageable pageable);
}
