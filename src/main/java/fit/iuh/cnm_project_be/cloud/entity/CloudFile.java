package fit.iuh.cnm_project_be.cloud.entity;

import fit.iuh.cnm_project_be.cloud.enums.CloudFileType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cloud_files")
@Getter
@Setter
public class CloudFile {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "parent_folder_id")
    private UUID parentFolderId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_extension")
    private String fileExtension;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private CloudFileType fileType;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "is_folder", nullable = false)
    private boolean isFolder;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "waveform")
    private String waveform;

    @Column(name = "audio_format")
    private String audioFormat;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "manual_content", columnDefinition = "text")
    private String manualContent;

    @Column(name = "analysis_status")
    private String analysisStatus;

    @Column(name = "detected_disease")
    private String detectedDisease;

    @Column(name = "severity_level")
    private String severityLevel;

    @Column(name = "analysis_confidence")
    private Double analysisConfidence;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
