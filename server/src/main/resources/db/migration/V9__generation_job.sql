create table generation_job (
    id               uuid        primary key,
    -- No foreign key, as the usage ledger: a job is the history of what a reader's key was spent on.
    account_id       uuid        not null,
    kind             text        not null check (kind in ('plan', 'post', 'check')),
    -- One live job per reader and piece of work: a second request for it gets the first job back.
    dedupe_key       text        not null,
    payload          jsonb       not null check (jsonb_typeof(payload) = 'object'),
    state            text        not null check (state in ('queued', 'running', 'succeeded', 'failed', 'cancelled')),
    -- Raised by every claim. Each write names its attempt, so one that lost its lease changes nothing.
    attempt          integer     not null default 0,
    -- A crashed attempt may already have billed, so a job runs twice at most.
    max_attempts     integer     not null default 2 check (max_attempts >= 1),
    run_after        timestamptz not null,
    lease_owner      text,
    lease_expires_at timestamptz,
    cancel_requested boolean     not null default false,
    error_kind       text,
    created_at       timestamptz not null,
    started_at       timestamptz,
    finished_at      timestamptz,
    check (attempt between 0 and max_attempts),
    check ((state = 'running') = (lease_owner is not null and lease_expires_at is not null)),
    check ((state in ('succeeded', 'failed', 'cancelled')) = (finished_at is not null)),
    check (state <> 'failed' or error_kind is not null)
);

create unique index generation_job_live_key on generation_job (account_id, dedupe_key)
    where state in ('queued', 'running');
create index generation_job_claim on generation_job (run_after, id) where state = 'queued';
create index generation_job_lease on generation_job (lease_expires_at) where state = 'running';
create index generation_job_account on generation_job (account_id, created_at desc);
