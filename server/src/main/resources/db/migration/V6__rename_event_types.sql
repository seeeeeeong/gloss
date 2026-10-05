-- The account events moved package; a delivery still waiting names the class it was stored under.
update event_publication
set event_type = 'gloss.account.domain.event.' || substring(event_type from length('gloss.domain.account.event.') + 1)
where event_type like 'gloss.domain.account.event.%';
