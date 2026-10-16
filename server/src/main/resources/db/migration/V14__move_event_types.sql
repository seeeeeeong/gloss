-- The modules moved under gloss.domain; a delivery still waiting names the class it was stored under.
update event_publication
set event_type = 'gloss.domain.' || substring(event_type from length('gloss.') + 1)
where event_type ~ '^gloss\.(account|apikey|audit|book|budget|generation|llm|plan|post)\.';
