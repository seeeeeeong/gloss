-- A reader keeps several conversations about a book; a new one starts a history of its own.
alter table chat_session
    drop constraint chat_session_account_id_book_id_key;

create index chat_session_book on chat_session (account_id, book_id);

-- A conversation is deleted whole, its messages with it. The ledger's rows for its calls stay.
alter table chat_message
    drop constraint chat_message_session_id_account_id_fkey,
    add constraint chat_message_session_id_account_id_fkey
        foreign key (session_id, account_id) references chat_session (id, account_id) on delete cascade;

-- What was said is never changed, and no message goes alone: only with its conversation, which the
-- cascade has removed by the time it deletes the message.
create function chat_message_goes_with_session() returns trigger
    language plpgsql as
$$
begin
    if exists (select 1 from chat_session where id = old.session_id) then
        raise exception 'chat_message is append-only: a conversation is deleted whole';
    end if;
    return old;
end;
$$;

drop trigger chat_message_append_only on chat_message;

create trigger chat_message_append_only
    before update on chat_message
    for each row execute function forbid_change();

create trigger chat_message_goes_with_session
    before delete on chat_message
    for each row execute function chat_message_goes_with_session();
