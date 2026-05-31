UPDATE moments
SET media_type = UPPER(media_type)
WHERE media_type IS NOT NULL
  AND media_type <> UPPER(media_type);

ALTER TABLE moments
    DROP CONSTRAINT IF EXISTS moments_media_type_check;

ALTER TABLE moments
    ADD CONSTRAINT moments_media_type_check
        CHECK (media_type IN ('IMAGE', 'VIDEO'));
