create table account (
    id                   uuid primary key,
    version              bigint      not null,
    -- Same format as LoginIdPolicy, which lowercases before storing.
    login_id             text        not null unique check (login_id ~ '^[a-z0-9][a-z0-9._-]{2,31}$'),
    -- 50 fits the top bar and the operator's account list.
    display_name         text        not null check (char_length(display_name) between 1 and 50),
    password_hash        text        not null,
    role                 text        not null check (role in ('operator', 'member')),
    status               text        not null check (status in ('active', 'disabled')),
    must_change_password boolean     not null,
    -- Raised on a password change, reset or suspension; a session holding an older value is ended.
    session_epoch        integer     not null check (session_epoch >= 0),
    created_by           uuid references account (id),
    created_at           timestamptz not null,
    password_changed_at  timestamptz,
    last_login_at        timestamptz,
    disabled_at          timestamptz,
    check ((status = 'disabled') = (disabled_at is not null))
);
