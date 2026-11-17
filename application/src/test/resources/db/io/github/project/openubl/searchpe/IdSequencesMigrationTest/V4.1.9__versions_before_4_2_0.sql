-- Runs before V4.2.0: a database that kept only two versions with high ids, as the daily cleanup leaves it.
-- V4.2.0 then seeds version_sequence with count(id) + 1 = 3.
insert into VERSION(id, status, records, created_at, updated_at, version)
values (100, 'COMPLETED', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       (150, 'COMPLETED', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);
