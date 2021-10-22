insert into VERSION(id, status, records, created_at, updated_at, version)
values (1, 'ERROR', 1000000, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day', 1),
       (2, 'COMPLETED', 5000000, CURRENT_TIMESTAMP - INTERVAL '2 day', CURRENT_TIMESTAMP - INTERVAL '2 day', 1),
       (3, 'COMPLETED', 9000000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);

insert into CONTRIBUYENTE(version_id, ruc, razon_social)
values (3, '11111111111', 'mi empresa1'),
       (3, '22222222222', 'mi empresa2');