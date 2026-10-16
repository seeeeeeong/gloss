-- A model now answers a reader in a setup session, on the reader's key: its calls are their own purpose.
alter table usage_ledger drop constraint usage_ledger_purpose_check;

alter table usage_ledger add constraint usage_ledger_purpose_check
    check (purpose in ('chat', 'plan', 'post', 'check', 'setup'));

-- What a model turn keeps: the calls it made with their arguments as written, what each tool answered
-- (for a book search only the words and the refs, for a web search only the words and how many came
-- back), the books it recommended with the pages it cited, and what it said.
alter table setup_message drop constraint setup_message_check;

alter table setup_message add constraint setup_message_check
    check ((role = 'reader' and kind in ('text', 'pick', 'typed', 'confirm'))
        or (role = 'gloss' and kind in ('books', 'search_failed', 'contents', 'done', 'tool_calls',
                                        'web_searched', 'tool_refused', 'recommend', 'books_shown', 'said')));

-- Every web search asked on the operator's key, for its daily caps and its cost. Written when it is
-- asked, so one still under way counts toward the caps; answered once, then kept as it is.
create table web_search (
    id          uuid primary key,
    account_id  uuid        not null,
    session_id  uuid        not null,
    asked_at    timestamptz not null,
    answered_at timestamptz,
    outcome     text check (outcome in ('answered', 'failed')),
    results     integer check (results >= 0),
    -- Exa's own estimate of what the search cost (costDollars.total), in µUSD; not its invoice.
    cost_micros bigint check (cost_micros >= 0),
    request_id  text check (char_length(request_id) between 1 and 200),
    foreign key (session_id, account_id) references setup_session (id, account_id),
    check ((answered_at is null) = (outcome is null)),
    check (answered_at is null or answered_at >= asked_at),
    check (outcome = 'answered' or (results is null and cost_micros is null and request_id is null))
);

create index web_search_reader on web_search (account_id, asked_at);
create index web_search_recent on web_search (asked_at);

create function web_search_answer_once() returns trigger
    language plpgsql as
$$
begin
    if tg_op = 'DELETE' or old.answered_at is not null
        or new.id <> old.id or new.account_id <> old.account_id
        or new.session_id <> old.session_id or new.asked_at <> old.asked_at then
        raise exception 'web_search is answered once and kept';
    end if;
    return new;
end;
$$;

create trigger web_search_answer_once
    before update or delete on web_search
    for each row execute function web_search_answer_once();
