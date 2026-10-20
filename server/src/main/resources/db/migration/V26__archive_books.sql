-- A reader takes a book off their shelf and may add it again: its posts, conversations and the
-- ledger stay, so it is hidden, never deleted. The partial indexes follow the renamed column.
alter table book
    rename column deleted_at to archived_at;
