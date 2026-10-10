create table book (
    id             uuid primary key,
    version        bigint      not null,
    account_id     uuid        not null references account (id),
    title          text        not null check (char_length(title) between 1 and 300),
    authors        text[]      not null,
    publisher      text check (char_length(publisher) between 1 and 100),
    isbn13         text check (isbn13 ~ '^97[89][0-9]{10}$'),
    -- An edition as printed, "개정판 2쇄" or "Second Edition, Revised", with room to spare.
    edition        text check (char_length(edition) between 1 and 100),
    -- Where the book's details came from; a source's own id for it, when it has one.
    source         text        not null check (source in ('manual', 'kakao', 'nlk', 'openlibrary')),
    source_ref     text,
    current_toc_id uuid,
    created_at     timestamptz not null,
    deleted_at     timestamptz,
    unique (id, account_id)
);

create unique index book_isbn13 on book (account_id, isbn13) where deleted_at is null and isbn13 is not null;
create index book_shelf on book (account_id, created_at desc) where deleted_at is null;

-- One row per version: an edit is a new draft, and a confirmed one never changes again.
create table toc (
    id           uuid primary key,
    version      bigint      not null,
    account_id   uuid        not null,
    book_id      uuid        not null,
    version_no   integer     not null check (version_no >= 1),
    origin       text        not null check (origin in ('api', 'manual')),
    source_url   text,
    raw          text        not null check (char_length(raw) between 1 and 100000),
    raw_sha256   text        not null check (raw_sha256 ~ '^[0-9a-f]{64}$'),
    status       text        not null check (status in ('draft', 'confirmed')),
    confirmed_at timestamptz,
    created_at   timestamptz not null,
    unique (book_id, version_no),
    unique (id, account_id),
    unique (id, book_id),
    foreign key (book_id, account_id) references book (id, account_id),
    check ((status = 'confirmed') = (confirmed_at is not null))
);

alter table book
    add foreign key (current_toc_id, id) references toc (id, book_id);

create function toc_only_confirms() returns trigger
    language plpgsql as
$$
begin
    if tg_op = 'DELETE' then
        raise exception 'toc is kept';
    end if;
    if old.status <> 'draft' or new.status <> 'confirmed'
        or (new.account_id, new.book_id, new.version_no, new.origin, new.source_url, new.raw, new.raw_sha256,
            new.created_at)
           is distinct from
           (old.account_id, old.book_id, old.version_no, old.origin, old.source_url, old.raw, old.raw_sha256,
            old.created_at) then
        raise exception 'a toc only goes from draft to confirmed';
    end if;
    return new;
end;
$$;

create trigger toc_only_confirms
    before update or delete on toc
    for each row execute function toc_only_confirms();

create table toc_item (
    id         uuid primary key,
    version    bigint  not null,
    account_id uuid    not null,
    toc_id     uuid    not null,
    parent_id  uuid,
    -- Among its siblings; ord is its place in the whole list, as read.
    position   integer not null check (position >= 0),
    ord        integer not null check (ord >= 0),
    depth      integer not null check (depth between 1 and 6),
    number     text check (char_length(number) between 1 and 50),
    title      text    not null check (char_length(title) between 1 and 500),
    -- Names the same section across versions, so posts and chats follow it to a new one.
    item_key   text    not null check (char_length(item_key) between 1 and 600),
    unique nulls not distinct (toc_id, parent_id, position),
    unique (toc_id, ord),
    unique (toc_id, item_key),
    unique (toc_id, id),
    foreign key (toc_id, account_id) references toc (id, account_id),
    foreign key (toc_id, parent_id) references toc_item (toc_id, id),
    check ((parent_id is null) = (depth = 1))
);

create trigger toc_item_append_only
    before update or delete on toc_item
    for each row execute function forbid_change();
