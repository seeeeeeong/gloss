create table api_key (
    id           uuid primary key,
    version      bigint      not null,
    account_id   uuid        not null references account (id),
    provider     text        not null check (provider in ('deepseek')),
    -- AES-256-GCM; cleared when revoked, so a key the reader removed cannot be opened again.
    ciphertext   bytea,
    nonce        bytea check (length(nonce) = 12),
    key_version  integer     not null check (key_version > 0),
    last4        text        not null check (char_length(last4) = 4),
    status       text        not null check (status in ('valid', 'invalid', 'unreadable', 'revoked')),
    validated_at timestamptz,
    last_used_at timestamptz,
    last_error   text,
    created_at   timestamptz not null,
    revoked_at   timestamptz,
    check ((status = 'revoked') = (revoked_at is not null)),
    check (case when revoked_at is null then ciphertext is not null and nonce is not null
                else ciphertext is null and nonce is null end)
);

create unique index api_key_in_use on api_key (account_id, provider) where revoked_at is null;
