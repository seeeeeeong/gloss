-- What every post of a book is written from: the order, the shared terms, each section's scope.
create table book_plan (
    id             uuid primary key,
    version        bigint      not null,
    account_id     uuid        not null,
    book_id        uuid        not null,
    toc_id         uuid        not null,
    version_no     integer     not null check (version_no >= 1),
    -- Text, not jsonb: each post's prompt carries these very bytes, and the provider's cache matches bytes.
    canonical_json text        not null check (canonical_json is json object),
    json_sha256    text        not null check (json_sha256 ~ '^[0-9a-f]{64}$'),
    model          text        not null check (char_length(model) between 1 and 100),
    prompt_version text        not null check (char_length(prompt_version) between 1 and 50),
    created_at     timestamptz not null,
    unique (toc_id, version_no),
    unique (id, book_id),
    foreign key (toc_id, book_id) references toc (id, book_id),
    foreign key (toc_id, account_id) references toc (id, account_id)
);

create trigger book_plan_append_only
    before update or delete on book_plan
    for each row execute function forbid_change();

-- One per section of a book, by the key that follows the section across versions of the contents.
create table post (
    id                 uuid primary key,
    version            bigint      not null,
    account_id         uuid        not null,
    book_id            uuid        not null,
    item_key           text        not null check (char_length(item_key) between 1 and 600),
    -- The version a reader sees; moved in the transaction that adds a version, so never half done.
    current_version_id uuid,
    -- Written from a plan that has since been replaced.
    stale              boolean     not null,
    created_at         timestamptz not null,
    unique (book_id, item_key),
    unique (id, account_id),
    unique (id, book_id),
    foreign key (book_id, account_id) references book (id, account_id)
);

-- Only an answer that finished and passed its checks is added, so a row never changes.
create table post_version (
    id                uuid primary key,
    version           bigint      not null,
    account_id        uuid        not null,
    book_id           uuid        not null,
    post_id           uuid        not null,
    version_no        integer     not null check (version_no >= 1),
    plan_id           uuid        not null,
    -- A section's article runs to a few thousand characters; far past that is a generation run away.
    content_md        text        not null check (char_length(content_md) between 1 and 200000),
    content_sha256    text        not null check (content_sha256 ~ '^[0-9a-f]{64}$'),
    model             text        not null check (char_length(model) between 1 and 100),
    prompt_version    text        not null check (char_length(prompt_version) between 1 and 50),
    finish_reason     text        not null check (finish_reason = 'stop'),
    cache_hit_tokens  integer     not null check (cache_hit_tokens >= 0),
    cache_miss_tokens integer     not null check (cache_miss_tokens >= 0),
    completion_tokens integer     not null check (completion_tokens >= 0),
    -- The job that wrote it. No foreign key: the version outlives a queue that is cleared.
    job_id            uuid,
    generated_at      timestamptz not null,
    unique (post_id, version_no),
    unique (id, post_id),
    foreign key (post_id, account_id) references post (id, account_id),
    foreign key (post_id, book_id) references post (id, book_id),
    foreign key (plan_id, book_id) references book_plan (id, book_id)
);

alter table post
    add foreign key (current_version_id, id) references post_version (id, post_id);

create trigger post_version_append_only
    before update or delete on post_version
    for each row execute function forbid_change();
