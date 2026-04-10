ALTER TABLE accounts
    ADD COLUMN email VARCHAR(255);

ALTER TABLE accounts
    ADD CONSTRAINT uk_accounts_email UNIQUE (email);
