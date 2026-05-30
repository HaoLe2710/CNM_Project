CREATE TABLE IF NOT EXISTS cloud_files (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    parent_folder_id UUID NULL,
    name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NULL,
    mime_type VARCHAR(120) NULL,
    file_extension VARCHAR(30) NULL,
    file_size BIGINT NULL,
    file_type VARCHAR(30) NOT NULL,
    file_url TEXT NULL,
    storage_key TEXT NULL,
    is_folder BOOLEAN NOT NULL DEFAULT FALSE,
    checksum VARCHAR(128) NULL,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cloud_files_owner_parent_created
    ON cloud_files (owner_id, parent_folder_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_cloud_files_owner_type
    ON cloud_files (owner_id, file_type)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_cloud_files_owner_name
    ON cloud_files (owner_id, name)
    WHERE deleted_at IS NULL;
