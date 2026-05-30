DO $$
DECLARE
    current_udt_name TEXT;
    legacy_column_exists BOOLEAN;
BEGIN
    SELECT udt_name
    INTO current_udt_name
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'notifications'
      AND column_name = 'message_id';

    IF current_udt_name = 'uuid' THEN
        SELECT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'notifications'
              AND column_name = 'legacy_message_id_uuid'
        )
        INTO legacy_column_exists;

        IF legacy_column_exists THEN
            ALTER TABLE notifications DROP COLUMN message_id;
        ELSE
            ALTER TABLE notifications RENAME COLUMN message_id TO legacy_message_id_uuid;
        END IF;

        ALTER TABLE notifications ADD COLUMN message_id BIGINT NULL;
    END IF;
END $$;
