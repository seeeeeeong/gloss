-- A job's writing of its post, taken before the model is called, by the attempt that holds the job:
-- a job writes its post twice at most, over all its attempts, however they race.
create table post_writing (
    id         bigint generated always as identity primary key,
    -- No foreign keys, as generation_call: a writing is history, and outlives the queue.
    account_id uuid        not null,
    job_id     uuid        not null,
    writing    smallint    not null check (writing in (1, 2)),
    attempt    integer     not null check (attempt >= 1),
    claimed_at timestamptz not null,
    unique (account_id, job_id, writing)
);

create trigger post_writing_append_only
    before update or delete on post_writing
    for each row execute function forbid_change();

-- Every writing of a post as it was judged before anything was kept: a post that fails is written
-- once more, and one that fails again is never kept.
create table post_check (
    id             bigint generated always as identity primary key,
    job_id         uuid        not null,
    attempt        integer     not null check (attempt >= 1),
    writing        smallint    not null,
    account_id     uuid        not null,
    book_id        uuid        not null,
    plan_id        uuid        not null,
    -- As post.item_key bounds it.
    item_key       text        not null check (char_length(item_key) between 1 and 600),
    prompt_version text        not null check (char_length(prompt_version) between 1 and 50),
    -- This application's own code, as generation_call keeps it: an unknown one is "unknown".
    finish_reason  text        not null check (char_length(finish_reason) between 1 and 50),
    passed         boolean     not null,
    failed_checks  text[]      not null check (failed_checks <@ array ['finish', 'empty', 'title', 'opening', 'sections']
        and array_position(failed_checks, null) is null),
    checked_at     timestamptz not null,
    check (passed = (cardinality(failed_checks) = 0)),
    -- A writing is judged once, and only one that was taken.
    unique (account_id, job_id, writing),
    foreign key (account_id, job_id, writing) references post_writing (account_id, job_id, writing)
);

create trigger post_check_append_only
    before update or delete on post_check
    for each row execute function forbid_change();
