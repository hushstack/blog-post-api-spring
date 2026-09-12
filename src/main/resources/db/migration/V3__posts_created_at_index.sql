-- The feed now pages over every PUBLIC post rather than one author's rows, so the
-- (author_id, created_at) index no longer serves it: a top-N on created_at needs an
-- index of its own, or Postgres sorts the whole table for every page.
create index ix_posts_created_at on posts (created_at);
