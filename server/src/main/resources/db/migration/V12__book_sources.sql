-- A book is typed in or comes from YES24's Open API; the sources named before it were never used.
alter table book drop constraint book_source_check;
alter table book add constraint book_source_check check (source in ('manual', 'yes24'));

-- The source's page for the book, shown wherever its details are: YES24's terms ask for the link.
alter table book add column source_url text;

alter table book add constraint book_source_ref_check check (char_length(source_ref) between 1 and 50);
alter table book add constraint book_source_url_check check (char_length(source_url) between 1 and 500);

-- A sourced book says which item it is and where to see it; a typed one has neither.
alter table book
    add constraint book_sourced_check
        check ((source = 'manual') = (source_ref is null) and (source = 'manual') = (source_url is null));
