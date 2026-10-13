-- Every model call a job made, however it ended: which job a reader's key was spent on, and how
-- much of each prompt the provider's cache held.
create table generation_call (
    id                bigint generated always as identity primary key,
    -- No foreign keys, as the usage ledger: a call is history, and outlives a queue that is cleared.
    job_id            uuid        not null,
    attempt           integer     not null check (attempt >= 1),
    account_id        uuid        not null,
    purpose           text        not null check (purpose in ('plan', 'post', 'check')),
    -- As book_plan and post_version bound them: a plan or a post names the same model and prompt.
    model             text        not null check (char_length(model) between 1 and 100),
    prompt_version    text        not null check (char_length(prompt_version) between 1 and 50),
    -- Null when the call ended without an answer; then error_code says why. Both are this
    -- application's own codes, never a provider's text: a finish reason it does not know is kept
    -- as "unknown", and the longest of either is under thirty characters.
    finish_reason     text check (char_length(finish_reason) between 1 and 50),
    error_code        text check (char_length(error_code) between 1 and 100),
    cache_hit_tokens  bigint check (cache_hit_tokens >= 0),
    cache_miss_tokens bigint check (cache_miss_tokens >= 0),
    completion_tokens bigint check (completion_tokens >= 0),
    reasoning_tokens  bigint check (reasoning_tokens >= 0),
    -- The ledger's rows for the call. Null when it failed: then the ledger alone says what it kept.
    reservation_id    uuid,
    occurred_at       timestamptz not null,
    check ((finish_reason is null) <> (error_code is null)),
    -- A provider reports the prompt's two halves and the answer together, or nothing.
    check ((cache_hit_tokens is null) = (cache_miss_tokens is null)
        and (cache_hit_tokens is null) = (completion_tokens is null))
);

create index generation_call_job on generation_call (job_id, attempt);

create trigger generation_call_append_only
    before update or delete on generation_call
    for each row execute function forbid_change();
