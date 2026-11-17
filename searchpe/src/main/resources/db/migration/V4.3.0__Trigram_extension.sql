-- Trigram indexes make the advanced search (lower(nombre) like '%text%') use an index instead of scanning the whole
-- partition. pg_trgm is a trusted extension since PostgreSQL 13, so the database owner can create it.
create extension if not exists pg_trgm;
