-- The web page an edition's detailed contents were found on and checked against, so the reader, and
-- the session that made a book from them, can say where they came from. Its text is not kept: neither
-- the search's terms (Exa ToS 4.2(a)) nor the page's own grant a right to store it. The hash of the text
-- the check read says whether the page still reads as it did. Kept as written.
create table source_page (
    id          uuid primary key,
    account_id  uuid        not null,
    session_id  uuid        not null,
    -- The scheme in any case, as the address policy takes it; the gateway writes it in lowercase.
    url         text        not null check (char_length(url) between 9 and 2000 and url ilike 'https://%'),
    title       text        not null check (char_length(title) between 1 and 300),
    fetched_at  timestamptz not null,
    text_sha256 text        not null check (text_sha256 ~ '^[0-9a-f]{64}$'),
    -- How much of the page the check read, in characters; at most what the model is shown of one.
    characters  integer     not null check (characters between 1 and 20000),
    unique (id, session_id),
    foreign key (session_id, account_id) references setup_session (id, account_id)
);

create trigger source_page_append_only
    before update or delete on source_page
    for each row execute function forbid_change();

-- Held contents found on the web name their page; a pick or contents typed in let it go. Only an
-- edition's contents are looked for, so a book typed in has none.
alter table setup_session add column contents_source_id uuid;

alter table setup_session
    add constraint setup_session_contents_source_fkey
        foreign key (contents_source_id, id) references source_page (id, session_id);

alter table setup_session
    add constraint setup_session_contents_source_check
        check (contents_source_id is null or edition_ref is not null);

-- The reader asks for an edition's contents from another source, and Gloss keeps the pages it read
-- looking for them: their address, title and length, never their text.
alter table setup_message drop constraint setup_message_check;

alter table setup_message add constraint setup_message_check
    check ((role = 'reader' and kind in ('text', 'pick', 'typed', 'confirm', 'seek'))
        or (role = 'gloss' and kind in ('books', 'search_failed', 'contents', 'done', 'tool_calls',
                                        'web_searched', 'tool_refused', 'recommend', 'books_shown', 'said',
                                        'page_fetched')));
