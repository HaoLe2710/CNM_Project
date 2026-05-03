package fit.iuh.cnm_project_be.social.repository;

import fit.iuh.cnm_project_be.common.repository.SoftDeleteRepository;
import fit.iuh.cnm_project_be.social.entity.Moment;
import fit.iuh.cnm_project_be.social.enums.MediaType;
import fit.iuh.cnm_project_be.social.enums.MomentVisibilityMode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MomentRepository
        extends SoftDeleteRepository<Moment, UUID> {

    List<Moment> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    @Query("""
        select m from Moment m
        where m.deletedAt is null
          and m.userId in :userIds
          and m.createdAt >= :fromTime
        order by m.createdAt desc
    """)
    List<Moment> findStoryFeedByUserIds(
            @Param("userIds") Collection<UUID> userIds,
            @Param("fromTime") Instant fromTime,
            Pageable pageable);

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

    @Query("""
        select m from Moment m
        where m.deletedAt is null
          and m.mediaType = fit.iuh.cnm_project_be.social.enums.MediaType.VIDEO
          and m.visibilityMode = fit.iuh.cnm_project_be.social.enums.MomentVisibilityMode.PUBLIC
          and (
                :cursorCreatedAt is null
                or m.createdAt < :cursorCreatedAt
                or (m.createdAt = :cursorCreatedAt and m.id < :cursorId)
          )
        order by m.createdAt desc, m.id desc
    """)
    List<Moment> findPublicCommunityVideos(
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    long countByMediaTypeAndVisibilityModeAndDeletedAtIsNull(MediaType mediaType, MomentVisibilityMode visibilityMode);
}
