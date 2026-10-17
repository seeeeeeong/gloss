-- How a book's posts are written, as the reader chose when adding it. Every book before the choice
-- keeps the way its posts were already written: for course readers, in the -다 textbook voice.
alter table book
    add column level text not null default 'course' check (level in ('intro', 'course', 'work')),
    add column voice text not null default 'textbook' check (voice in ('textbook', 'explain', 'note'));
