-- What tells one edition from another where the source names none: when it came out and what the
-- source sells it as, "도서" or "eBook". A book added before, or typed in, has neither.
alter table book
    add column published_on date,
    add column kind text check (char_length(kind) between 1 and 20);
