-- Start hibernate_sequence from 1000 to avoid conflicts with the IDs set in VERSION table
alter sequence hibernate_sequence restart with 1000;

insert into VERSION(id, status, records, created_at, updated_at, version)
values (1, 'ERROR', 1000000, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day', 1),
       (2, 'COMPLETED', 5000000, CURRENT_TIMESTAMP - INTERVAL '2 day', CURRENT_TIMESTAMP - INTERVAL '2 day', 1),
       (3, 'COMPLETED', 9000000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);

insert into CONTRIBUYENTE(version_id, tipo_persona, numero_documento, nombre)
values (3, 'JURIDICA', '11111111111', 'mi empresa1'),
       (3, 'JURIDICA', '22222222222', 'mi empresa2');