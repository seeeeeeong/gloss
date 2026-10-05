create table audit_event (
    id          bigint generated always as identity primary key,
    -- The event's own id: a delivery repeated after a crash adds nothing.
    event_id    uuid        not null unique,
    occurred_at timestamptz not null,
    -- No foreign keys: the record outlives whatever it names and stays out of other modules' tables.
    actor_id    uuid,
    subject_id  uuid,
    kind        text        not null,
    detail      jsonb       not null check (jsonb_typeof(detail) = 'object')
);

create trigger audit_event_append_only
    before update or delete on audit_event
    for each row execute function forbid_change();
