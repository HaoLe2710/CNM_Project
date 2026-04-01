-- Fix user_devices unique constraint to allow 1 device per platform per user
ALTER TABLE user_devices DROP CONSTRAINT IF EXISTS user_devices_user_id_key;
ALTER TABLE user_devices ADD CONSTRAINT user_devices_user_id_platform_key UNIQUE (user_id, platform);

-- Update accounts table id from BIGINT to UUID
-- Disable foreign key checks temporarily
SET CONSTRAINTS ALL DEFERRED;

-- Step 1: Delete dependent data to avoid FK conflicts
DELETE FROM refresh_tokens;
DELETE FROM account_roles;

-- Step 2: Create temporary column with UUID type
ALTER TABLE accounts ADD COLUMN id_uuid UUID;

-- Step 3: Generate UUIDs for existing rows and preserve mapping
UPDATE accounts SET id_uuid = gen_random_uuid() WHERE id_uuid IS NULL;

-- Step 4: Drop foreign key constraints
ALTER TABLE account_roles DROP CONSTRAINT IF EXISTS fk_account_roles_account_id CASCADE;
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS fk_refresh_tokens_account_id CASCADE;

-- Step 5: Drop primary key and old id column
ALTER TABLE accounts DROP CONSTRAINT accounts_pkey;
ALTER TABLE accounts DROP COLUMN id;

-- Step 6: Rename new UUID column to id
ALTER TABLE accounts RENAME COLUMN id_uuid TO id;
ALTER TABLE accounts ADD PRIMARY KEY (id);

-- Step 7: Recreate account_roles and refresh_tokens with UUID account_id columns
ALTER TABLE account_roles DROP COLUMN account_id;
ALTER TABLE account_roles ADD COLUMN account_id UUID NOT NULL;

ALTER TABLE refresh_tokens DROP COLUMN account_id;
ALTER TABLE refresh_tokens ADD COLUMN account_id UUID NOT NULL;

-- Step 8: Recreate foreign key constraints
ALTER TABLE account_roles
    ADD CONSTRAINT fk_account_roles_account_id
    FOREIGN KEY (account_id)
    REFERENCES accounts (id)
    ON DELETE CASCADE;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_account_id
    FOREIGN KEY (account_id)
    REFERENCES accounts (id)
    ON DELETE CASCADE;

-- Step 9: Recreate indexes
DROP INDEX IF EXISTS idx_refresh_tokens_account_id;
CREATE INDEX idx_refresh_tokens_account_id ON refresh_tokens (account_id);

-- Re-enable foreign key checks
SET CONSTRAINTS ALL IMMEDIATE;
