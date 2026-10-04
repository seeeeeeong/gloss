-- Shared by every append-only table: a ledger row, once written, is history.
create function forbid_change() returns trigger
    language plpgsql as
$$
begin
    raise exception '% is append-only', tg_table_name;
end;
$$;

create table login_failure (
    id           bigint generated always as identity primary key,
    -- The login id as typed, trimmed and lowercased, so a misspelled or unknown one counts too.
    login_id_key text        not null check (char_length(login_id_key) between 1 and 64),
    ip           inet        not null,
    attempted_at timestamptz not null
);

create index login_failure_login_id_ip on login_failure (login_id_key, ip, attempted_at);
create index login_failure_ip on login_failure (ip, attempted_at);

create trigger login_failure_append_only
    before update or delete on login_failure
    for each row execute function forbid_change();
