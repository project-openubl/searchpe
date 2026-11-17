alter sequence version_sequence restart with 1000;

insert into VERSION(id, status, records, created_at, updated_at, version)
values (1, 'IMPORTING', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       (2, 'SCHEDULED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       (3, 'CANCELLING', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
       (4, 'INDEXING', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);
