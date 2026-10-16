-- A call that ended to run tools finished its turn, but not with an answer; it is kept apart from stop.
alter table usage_ledger drop constraint usage_ledger_outcome_check;

alter table usage_ledger add constraint usage_ledger_outcome_check
    check (outcome in ('stop', 'length', 'content_filter', 'tool_calls', 'cancelled_before_output',
                       'cancelled_after_output', 'error', 'expired'));
