-- A reader adding a book by talking it through: one session per book, from the first message to the
-- book made from the contents they started with.
create table setup_session (
    id            uuid primary key,
    version       bigint      not null,
    account_id    uuid        not null references account (id),
    state         text        not null check (state in ('searching', 'book_chosen', 'confirmed')),
    -- What the reader chose and its contents as they last left them; no book exists until they start.
    -- An edition a search found, or a book they typed in with its authors, never both; its title is
    -- kept here with the contents, so the two are always read together.
    edition_ref   text check (char_length(edition_ref) between 1 and 50),
    held_title    text check (char_length(held_title) between 1 and 300),
    typed_authors text[],
    contents      text check (char_length(contents) between 1 and 100000),
    -- Counts every change a reader could act on: each request names the one it saw, and a request
    -- made on an older one is refused, so one tab never overwrites what another did.
    revision      integer     not null default 0 check (revision >= 0),
    -- When the search for the reader's latest message was asked, until its answer is kept: the next
    -- message waits for it, so answers stay in the order of their messages.
    searching_since timestamptz,
    -- The book made at the start, and the version of its contents confirmed then.
    book_id       uuid,
    toc_id        uuid,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    confirmed_at  timestamptz,
    unique (id, account_id),
    foreign key (book_id, account_id) references book (id, account_id),
    foreign key (toc_id, book_id) references toc (id, book_id),
    check ((state = 'searching') = (contents is null)),
    check ((contents is null) = (held_title is null)),
    check ((contents is null) = (edition_ref is null and typed_authors is null)),
    check (edition_ref is null or typed_authors is null),
    check ((state = 'confirmed') = (book_id is not null)),
    check ((book_id is null) = (toc_id is null)),
    check ((state = 'confirmed') = (confirmed_at is not null)),
    check (updated_at >= created_at)
);

create index setup_session_reader on setup_session (account_id, created_at desc);

-- Append-only: a correction is a new message, as the model's cache will need once it reads them.
create table setup_message (
    id         uuid primary key,
    session_id uuid        not null,
    account_id uuid        not null,
    seq        integer     not null check (seq >= 1),
    role       text        not null check (role in ('reader', 'gloss')),
    kind       text        not null,
    payload    jsonb       not null check (jsonb_typeof(payload) = 'object'),
    created_at timestamptz not null,
    unique (session_id, seq),
    foreign key (session_id, account_id) references setup_session (id, account_id),
    -- Each kind is said by one side only; a new kind comes with a migration that widens this.
    check ((role = 'reader' and kind in ('text', 'pick', 'typed', 'confirm'))
        or (role = 'gloss' and kind in ('books', 'search_failed', 'contents', 'done')))
);

create trigger setup_message_append_only
    before update or delete on setup_message
    for each row execute function forbid_change();
