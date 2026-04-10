ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS phone VARCHAR(255);

ALTER TABLE accounts
    ADD CONSTRAINT uk_accounts_phone UNIQUE (phone);
