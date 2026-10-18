-- A phrase a reader picked in an item of a book; without one, the item itself.
create table bookmark (
    id         uuid primary key,
    version    bigint      not null,
    account_id uuid        not null references account (id),
    book_id    uuid        not null,
    item_key   text        not null check (char_length(item_key) between 1 and 600),
    quote      text check (char_length(quote) between 1 and 1000),
    created_at timestamptz not null,
    foreign key (book_id, account_id) references book (id, account_id)
);

-- The phrase goes in as its md5: a long one would pass the 2704 bytes a btree entry may hold.
create unique index bookmark_once on bookmark (account_id, book_id, item_key, md5(coalesce(quote, '')));

create index bookmark_newest on bookmark (account_id, created_at desc);
