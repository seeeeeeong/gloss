-- A reader's conversation with the tutor about one book, across all its items.
create table chat_session (
    id         uuid primary key,
    version    bigint      not null,
    account_id uuid        not null references account (id),
    book_id    uuid        not null,
    created_at timestamptz not null,
    unique (id, account_id),
    unique (account_id, book_id),
    foreign key (book_id, account_id) references book (id, account_id)
);

-- Append-only: the model's cache matches the prompt up to the first byte that changed, so what was
-- said stays as said, and a correction is a new message. Gloss's reply is written once it has ended.
create table chat_message (
    id                uuid primary key,
    session_id        uuid        not null,
    account_id        uuid        not null,
    seq               integer     not null check (seq >= 1),
    role              text        not null check (role in ('reader', 'gloss')),
    -- The item the reader was on when they asked, as post.item_key bounds it; none from the cover.
    item_key          text check (char_length(item_key) between 1 and 600),
    -- The phrase the reader picked in the item, if they asked about one.
    quote             text check (char_length(quote) between 1 and 1000),
    -- A reader's question as typed; a reply's text so far, which is empty when it ended before any.
    text              text        not null check (char_length(text) <= 200000),
    -- The reader's turn exactly as the model was sent it, and the version of the item's post it
    -- carried, if it was the first time that version went in.
    prompt            text check (char_length(prompt) between 1 and 400000),
    post_version_id   uuid,
    -- The page's own name for the turn, so a page that lost the answer finds the turn it sent.
    turn_id           uuid,
    -- The plan the turn was asked under: the history sent again is of turns under the plan in use.
    plan_id           uuid,
    -- The first turn the history sent with this one may hold; it moves only when the history outgrows
    -- its budget, so a turn reads the turns from here on and never the whole conversation.
    history_from      integer check (history_from >= 1),
    -- The reader message a reply answers; one reply each.
    reply_to          integer,
    status            text check (status in ('done', 'stopped', 'cut', 'failed')),
    -- This application's own codes, never a provider's text, as generation_call keeps them.
    finish_reason     text check (char_length(finish_reason) between 1 and 50),
    error_code        text check (char_length(error_code) between 1 and 100),
    model             text check (char_length(model) between 1 and 100),
    prompt_version    text check (char_length(prompt_version) between 1 and 50),
    cache_hit_tokens  bigint check (cache_hit_tokens >= 0),
    cache_miss_tokens bigint check (cache_miss_tokens >= 0),
    completion_tokens bigint check (completion_tokens >= 0),
    reasoning_tokens  bigint check (reasoning_tokens >= 0),
    -- The ledger's rows for the call; null when nothing was reserved.
    reservation_id    uuid,
    created_at        timestamptz not null,
    unique (session_id, seq),
    unique (session_id, reply_to),
    unique (session_id, turn_id),
    foreign key (session_id, account_id) references chat_session (id, account_id),
    foreign key (session_id, reply_to) references chat_message (session_id, seq),
    check (role = 'gloss' or (char_length(text) between 1 and 2000 and prompt is not null
        and turn_id is not null and plan_id is not null and history_from is not null and history_from <= seq
        and reply_to is null and status is null and finish_reason is null and error_code is null
        and model is null and prompt_version is null and cache_hit_tokens is null
        and reasoning_tokens is null and reservation_id is null)),
    check (role = 'reader' or (quote is null and prompt is null and post_version_id is null
        and turn_id is null and plan_id is null and history_from is null and reply_to is not null and status is not null
        and model is not null and prompt_version is not null)),
    check (reply_to is null or reply_to < seq),
    -- Only an answer the model finished is done; a failure always says why.
    check (status is null or status <> 'done' or finish_reason = 'stop'),
    check (status is null or status <> 'cut' or (finish_reason is not null and finish_reason <> 'stop')),
    check (status is null or (status = 'failed') = (error_code is not null)),
    -- A provider reports the prompt's two halves and the answer together, or nothing.
    check ((cache_hit_tokens is null) = (cache_miss_tokens is null)
        and (cache_hit_tokens is null) = (completion_tokens is null)),
    check (reasoning_tokens is null or cache_hit_tokens is not null)
);

-- A turn reads only the finished turns of its history, never the failed or stopped ones between them.
create index chat_message_done on chat_message (session_id, reply_to) where status = 'done';

create trigger chat_message_append_only
    before update or delete on chat_message
    for each row execute function forbid_change();
