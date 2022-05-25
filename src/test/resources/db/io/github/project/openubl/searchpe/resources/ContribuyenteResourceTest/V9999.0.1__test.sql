-- Start hibernate_sequence from 1000 to avoid conflicts with the IDs set in VERSION table
alter sequence hibernate_sequence restart with 1000;

insert into VERSION(id, status, records, created_at, updated_at, version)
values (1, 'ERROR', 1000000, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day', 1),
       (2, 'COMPLETED', 5000000, CURRENT_TIMESTAMP - INTERVAL '2 day', CURRENT_TIMESTAMP - INTERVAL '2 day', 1),
       (3, 'COMPLETED', 9000000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);

-- Contribuyentes
create table CONTRIBUYENTE_VERSION_1 partition of CONTRIBUYENTE for values from (1) TO (2);
create table CONTRIBUYENTE_VERSION_2 partition of CONTRIBUYENTE for values from (2) TO (3);
create table CONTRIBUYENTE_VERSION_3 partition of CONTRIBUYENTE for values from (3) TO (4);

insert into CONTRIBUYENTE(version_id, ruc, nombre)
values (3, '11111111111', 'mi empresa1'),
       (3, '22222222222', 'mi empresa2');