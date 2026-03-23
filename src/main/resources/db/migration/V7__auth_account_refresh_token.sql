-- Add AUTH tables introduced after initial schema

CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    username TEXT NOT NULL,
    user_id UUID NOT NULL,
    password TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS username TEXT,
    ADD COLUMN IF NOT EXISTS user_id UUID,
    ADD COLUMN IF NOT EXISTS password TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

UPDATE accounts
SET created_at = now()
WHERE created_at IS NULL;

ALTER TABLE accounts
    ALTER COLUMN username SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL,
    ALTER COLUMN password SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_accounts_username ON accounts (username);
CREATE UNIQUE INDEX IF NOT EXISTS uk_accounts_user_id ON accounts (user_id);

CREATE TABLE IF NOT EXISTS account_roles (
    account_id BIGINT NOT NULL,
    role TEXT NOT NULL,
    PRIMARY KEY (account_id, role),
    CONSTRAINT fk_account_roles_account_id
        FOREIGN KEY (account_id)
        REFERENCES accounts (id)
        ON DELETE CASCADE
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'account_roles_pkey'
    ) THEN
        ALTER TABLE account_roles
            ADD CONSTRAINT account_roles_pkey PRIMARY KEY (account_id, role);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_account_roles_account_id'
    ) THEN
        ALTER TABLE account_roles
            ADD CONSTRAINT fk_account_roles_account_id
            FOREIGN KEY (account_id)
            REFERENCES accounts (id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    token TEXT PRIMARY KEY,
    expiry_date TIMESTAMP NOT NULL,
    account_id BIGINT NOT NULL,
    CONSTRAINT fk_refresh_tokens_account_id
        FOREIGN KEY (account_id)
        REFERENCES accounts (id)
        ON DELETE CASCADE
);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS expiry_date TIMESTAMP,
    ADD COLUMN IF NOT EXISTS account_id BIGINT;

UPDATE refresh_tokens
SET expiry_date = now()
WHERE expiry_date IS NULL;

DELETE FROM refresh_tokens
WHERE account_id IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN expiry_date SET NOT NULL,
    ALTER COLUMN account_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_refresh_tokens_account_id'
    ) THEN
        ALTER TABLE refresh_tokens
            ADD CONSTRAINT fk_refresh_tokens_account_id
            FOREIGN KEY (account_id)
            REFERENCES accounts (id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_account_id
    ON refresh_tokens (account_id);
