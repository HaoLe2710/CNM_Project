package fit.iuh.cnm_project_be.cloud.repository;

import fit.iuh.cnm_project_be.cloud.entity.CloudFile;
import fit.iuh.cnm_project_be.cloud.enums.CloudFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.docker.compose.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class CloudFileRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CloudFileRepository cloudFileRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                create table if not exists cloud_files (
                    id uuid primary key,
                    owner_id uuid not null,
                    parent_folder_id uuid null,
                    name varchar(255) not null,
                    original_file_name varchar(255) null,
                    mime_type varchar(120) null,
                    file_extension varchar(30) null,
                    file_size bigint null,
                    file_type varchar(30) not null,
                    file_url varchar(1000) null,
                    storage_key varchar(1000) null,
                    is_folder boolean not null default false,
                    duration_ms bigint null,
                    waveform text null,
                    audio_format varchar(32) null,
                    checksum varchar(128) null,
                    deleted_at timestamp null,
                    created_at timestamp not null,
                    updated_at timestamp not null
                )
                """);
        jdbcTemplate.execute("delete from cloud_files");
    }

    @Test
    void findByOwnerAndParentSuccess() {
        UUID ownerId = UUID.randomUUID();

        CloudFile folder = saveFolder(ownerId, null, "Folder A");
        saveFile(ownerId, folder.getId(), "notes.txt", CloudFileType.DOCUMENT, 120L);
        saveFile(ownerId, null, "root.txt", CloudFileType.DOCUMENT, 80L);

        var result = cloudFileRepository.searchActiveFiles(
                ownerId,
                folder.getId(),
                null,
                null,
                PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getName()).isEqualTo("notes.txt");
    }

    @Test
    void searchByOwnerAndTypeSuccess() {
        UUID ownerId = UUID.randomUUID();

        saveFile(ownerId, null, "image-1.png", CloudFileType.IMAGE, 100L);
        saveFile(ownerId, null, "doc-1.pdf", CloudFileType.DOCUMENT, 200L);

        var result = cloudFileRepository.searchActiveFiles(
                ownerId,
                null,
                CloudFileType.IMAGE,
                null,
                PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getFileType()).isEqualTo(CloudFileType.IMAGE);
    }

    @Test
    void summaryQuerySuccess() {
        UUID ownerId = UUID.randomUUID();

        saveFolder(ownerId, null, "Folder");
        saveFile(ownerId, null, "img.png", CloudFileType.IMAGE, 1_000L);
        saveFile(ownerId, null, "doc.pdf", CloudFileType.DOCUMENT, 2_000L);

        assertThat(cloudFileRepository.sumActiveFileSizeByOwner(ownerId)).isEqualTo(3_000L);
        assertThat(cloudFileRepository.countByOwnerIdAndDeletedAtIsNullAndIsFolderFalse(ownerId)).isEqualTo(2);
        assertThat(cloudFileRepository.countByOwnerIdAndDeletedAtIsNullAndIsFolderTrue(ownerId)).isEqualTo(1);
        assertThat(cloudFileRepository.summarizeStorageByType(ownerId))
                .extracting(CloudFileRepository.CloudFileTypeUsageView::getFileType)
                .containsExactlyInAnyOrder(CloudFileType.IMAGE, CloudFileType.DOCUMENT);
    }

    @Test
    void trashQueryAndUsedSizeSuccess() {
        UUID ownerId = UUID.randomUUID();

        CloudFile active = saveFile(ownerId, null, "active.pdf", CloudFileType.DOCUMENT, 2_000L);
        CloudFile deleted = saveFile(ownerId, null, "deleted.pdf", CloudFileType.DOCUMENT, 1_500L);
        deleted.setDeletedAt(Instant.now());
        cloudFileRepository.saveAndFlush(deleted);

        var trashResult = cloudFileRepository.searchDeletedFiles(
                ownerId,
                null,
                "",
                PageRequest.of(0, 20));

        assertThat(trashResult.getTotalElements()).isEqualTo(1);
        assertThat(trashResult.getContent().getFirst().getId()).isEqualTo(deleted.getId());
        assertThat(cloudFileRepository.sumUsedFileSizeByOwner(ownerId)).isEqualTo(3_500L);
        assertThat(cloudFileRepository.sumTrashFileSizeByOwner(ownerId)).isEqualTo(1_500L);
        assertThat(cloudFileRepository.existsAnyChildren(ownerId, active.getId())).isFalse();
    }

    private CloudFile saveFolder(UUID ownerId, UUID parentFolderId, String name) {
        CloudFile folder = new CloudFile();
        folder.setOwnerId(ownerId);
        folder.setParentFolderId(parentFolderId);
        folder.setName(name);
        folder.setFileType(CloudFileType.FOLDER);
        folder.setFolder(true);
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());
        return cloudFileRepository.saveAndFlush(folder);
    }

    private CloudFile saveFile(
            UUID ownerId,
            UUID parentFolderId,
            String name,
            CloudFileType fileType,
            Long fileSize) {
        CloudFile file = new CloudFile();
        file.setOwnerId(ownerId);
        file.setParentFolderId(parentFolderId);
        file.setName(name);
        file.setOriginalFileName(name);
        file.setMimeType("application/octet-stream");
        file.setFileExtension(CloudFileType.extractExtension(name));
        file.setFileSize(fileSize);
        file.setFileType(fileType);
        file.setFileUrl("https://cdn.example.com/" + name);
        file.setStorageKey("cloud/" + ownerId + "/" + name);
        file.setFolder(false);
        file.setCreatedAt(Instant.now());
        file.setUpdatedAt(Instant.now());
        return cloudFileRepository.saveAndFlush(file);
    }
}
