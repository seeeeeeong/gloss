-- Where a reader last was in a book: one row a book, moved as they read on, on any device.
create table book_read (
    book_id    uuid primary key,
    account_id uuid        not null,
    item_key   text        not null check (char_length(item_key) between 1 and 600),
    read_at    timestamptz not null,
    foreign key (book_id, account_id) references book (id, account_id)
);

create index book_read_newest on book_read (account_id, read_at desc);
