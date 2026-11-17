alter sequence version_sequence restart with 1000;

insert into VERSION(id, status, records, created_at, updated_at, version)
values (1, 'COMPLETED', 10, CURRENT_TIMESTAMP - INTERVAL '2 day', CURRENT_TIMESTAMP - INTERVAL '2 day', 1),
       -- Active version: the most recent COMPLETED one
       (2, 'COMPLETED', 10, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day', 1),
       (3, 'ERROR', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       (4, 'CANCELLED', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       -- In progress and recently updated
       (5, 'IMPORTING', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       (6, 'SCHEDULED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       -- In progress but abandoned
       (7, 'IMPORTING', 10, CURRENT_TIMESTAMP - INTERVAL '7 hour', CURRENT_TIMESTAMP - INTERVAL '7 hour', 1),
       (8, 'DELETING', 10, CURRENT_TIMESTAMP - INTERVAL '7 hour', CURRENT_TIMESTAMP - INTERVAL '7 hour', 1);
