create table usage_ledger (
    id                bigint generated always as identity primary key,
    occurred_at       timestamptz not null,
    -- The first day of the month in Asia/Seoul that the row counts toward: the reservation's month,
    -- even for an ending written after midnight, so a month's rows always net out.
    billing_month     date        not null check (extract(day from billing_month) = 1),
    -- No foreign keys: the ledger is history and stays out of other modules' tables.
    account_id        uuid        not null,
    api_key_id        uuid,
    reservation_id    uuid        not null,
    kind              text        not null check (kind in ('reserve', 'settle', 'release', 'expire')),
    -- µUSD. reserve: the most the call can cost; settle: actual minus reserved; release: minus
    -- reserved; expire: nothing, so what was reserved stays spent. A month's sum is its spend.
    amount_micros     bigint      not null,
    purpose           text        not null check (purpose in ('chat', 'plan', 'post', 'check')),
    model             text        not null,
    cache_hit_tokens  integer check (cache_hit_tokens >= 0),
    cache_miss_tokens integer check (cache_miss_tokens >= 0),
    completion_tokens integer check (completion_tokens >= 0),
    reasoning_tokens  integer check (reasoning_tokens >= 0),
    -- µUSD per million tokens, copied onto every row so it reads the same after a price change.
    price_hit         bigint      not null check (price_hit >= 0),
    price_miss        bigint      not null check (price_miss >= 0),
    price_out         bigint      not null check (price_out >= 0),
    outcome           text check (outcome in ('stop', 'length', 'content_filter', 'cancelled_before_output',
                                               'cancelled_after_output', 'error', 'expired')),
    usage_estimated   boolean     not null default false,
    check ((kind = 'reserve') = (outcome is null)),
    check (kind <> 'reserve' or amount_micros > 0),
    check (kind <> 'release' or amount_micros <= 0),
    check (kind <> 'expire' or amount_micros = 0)
);

create unique index usage_ledger_one_reserve on usage_ledger (reservation_id) where kind = 'reserve';
-- A settle, release or expire: whichever comes first ends it, and a second is refused.
create unique index usage_ledger_one_ending on usage_ledger (reservation_id) where kind <> 'reserve';
create index usage_ledger_month on usage_ledger (account_id, billing_month) include (amount_micros);
create index usage_ledger_recent_reserves on usage_ledger (account_id, occurred_at) where kind = 'reserve';
-- For the expiry round, which reads reserves across accounts from its watermark on.
create index usage_ledger_reserves_by_time on usage_ledger (occurred_at) where kind = 'reserve';

-- How far the expiry round has looked: every reserve made before it has ended. One row, moving
-- forward, so a round reads the last minute of reserves and an outage is caught up, not skipped.
create table usage_ledger_expiry (
    id              smallint    primary key check (id = 1),
    scanned_through timestamptz not null
);

insert into usage_ledger_expiry (id, scanned_through) values (1, '1970-01-01 00:00:00+00');

create trigger usage_ledger_append_only
    before update or delete on usage_ledger
    for each row execute function forbid_change();

create table spending_limit (
    account_id           uuid        primary key references account (id),
    version              bigint      not null,
    -- Up to $1,000 a month: a larger number is far more likely a typo than an intent.
    monthly_limit_micros bigint      not null check (monthly_limit_micros between 1 and 1000000000),
    updated_at           timestamptz not null
);
