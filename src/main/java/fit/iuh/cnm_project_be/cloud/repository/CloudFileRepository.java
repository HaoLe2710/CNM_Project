package fit.iuh.cnm_project_be.cloud.repository;

import fit.iuh.cnm_project_be.cloud.entity.CloudFile;
import fit.iuh.cnm_project_be.cloud.enums.CloudFileType;
import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CloudFileRepository extends BaseRepository<CloudFile, UUID> {

    Optional<CloudFile> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<CloudFile> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

    @Query("""
            select cf from CloudFile cf
            where cf.ownerId = :ownerId
              and cf.deletedAt is null
              and (
                (:parentFolderId is null and cf.parentFolderId is null)
                or cf.parentFolderId = :parentFolderId
              )
              and (:fileType is null or cf.fileType = :fileType)
              and (
                coalesce(:query, '') = ''
                or lower(cf.name) like lower(concat('%', :query, '%'))
                or lower(coalesce(cf.originalFileName, '')) like lower(concat('%', :query, '%'))
              )
            order by
              case when cf.isFolder = true then 0 else 1 end,
              coalesce(cf.updatedAt, cf.createdAt) desc,
              cf.createdAt desc
            """)
    Page<CloudFile> searchActiveFiles(
            @Param("ownerId") UUID ownerId,
            @Param("parentFolderId") UUID parentFolderId,
            @Param("fileType") CloudFileType fileType,
            @Param("query") String query,
            Pageable pageable);

    @Query("""
            select cf from CloudFile cf
            where cf.ownerId = :ownerId
              and cf.deletedAt is not null
              and (:fileType is null or cf.fileType = :fileType)
              and (
                coalesce(:query, '') = ''
                or lower(cf.name) like lower(concat('%', :query, '%'))
                or lower(coalesce(cf.originalFileName, '')) like lower(concat('%', :query, '%'))
              )
            order by
              cf.deletedAt desc,
              coalesce(cf.updatedAt, cf.createdAt) desc,
              cf.createdAt desc
            """)
    Page<CloudFile> searchDeletedFiles(
            @Param("ownerId") UUID ownerId,
            @Param("fileType") CloudFileType fileType,
            @Param("query") String query,
            Pageable pageable);

    @Query("""
            select count(cf) > 0 from CloudFile cf
            where cf.ownerId = :ownerId
              and cf.parentFolderId = :parentFolderId
              and cf.deletedAt is null
            """)
    boolean existsActiveChildren(
            @Param("ownerId") UUID ownerId,
            @Param("parentFolderId") UUID parentFolderId);

    @Query("""
            select count(cf) > 0 from CloudFile cf
            where cf.ownerId = :ownerId
              and cf.parentFolderId = :parentFolderId
            """)
    boolean existsAnyChildren(
            @Param("ownerId") UUID ownerId,
            @Param("parentFolderId") UUID parentFolderId);

    @Query("""
            select coalesce(sum(cf.fileSize), 0) from CloudFile cf
            where cf.ownerId = :ownerId
              and cf.deletedAt is null
              and cf.isFolder = false
            """)
    Long sumActiveFileSizeByOwner(@Param("ownerId") UUID ownerId);

    @Query("""
            select coalesce(sum(cf.fileSize), 0) from CloudFile cf
            where cf.ownerId = :ownerId
              and cf.deletedAt is not null
              and cf.isFolder = false
            """)
    Long sumTrashFileSizeByOwner(@Param("ownerId") UUID ownerId);

    @Query("""
            select coalesce(sum(cf.fileSize), 0) from CloudFile cf
            where cf.ownerId = :ownerId
              and cf.isFolder = false
            """)
    Long sumUsedFileSizeByOwner(@Param("ownerId") UUID ownerId);

    long countByOwnerIdAndDeletedAtIsNullAndIsFolderFalse(UUID ownerId);

    long countByOwnerIdAndDeletedAtIsNullAndIsFolderTrue(UUID ownerId);

    @Query("""
            select cf.fileType as fileType, coalesce(sum(cf.fileSize), 0) as totalBytes
            from CloudFile cf
            where cf.ownerId = :ownerId
              and cf.deletedAt is null
              and cf.isFolder = false
            group by cf.fileType
            """)
    List<CloudFileTypeUsageView> summarizeStorageByType(@Param("ownerId") UUID ownerId);

    interface CloudFileTypeUsageView {
        CloudFileType getFileType();

        Long getTotalBytes();
    }
}
