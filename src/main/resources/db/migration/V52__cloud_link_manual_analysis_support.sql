ALTER TABLE cloud_files
    ADD COLUMN IF NOT EXISTS manual_content text;

ALTER TABLE cloud_files
    ADD COLUMN IF NOT EXISTS analysis_status varchar(255);

ALTER TABLE cloud_files
    ADD COLUMN IF NOT EXISTS detected_disease varchar(255);

ALTER TABLE cloud_files
    ADD COLUMN IF NOT EXISTS severity_level varchar(100);

ALTER TABLE cloud_files
    ADD COLUMN IF NOT EXISTS analysis_confidence double precision;

ALTER TABLE cloud_files
    ADD COLUMN IF NOT EXISTS analyzed_at timestamptz;
