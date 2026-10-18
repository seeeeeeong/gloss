-- Contents found on another site stay held but no longer name the page. The message kinds that
-- search wrote stay allowed: older sessions still hold them and are still read.
alter table setup_session
    drop constraint setup_session_contents_source_check,
    drop constraint setup_session_contents_source_fkey,
    drop column contents_source_id;

drop table source_page;
