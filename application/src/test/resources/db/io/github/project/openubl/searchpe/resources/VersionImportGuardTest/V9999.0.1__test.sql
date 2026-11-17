alter sequence version_sequence restart with 1000;

-- Active version with more than twice the records of the padrón served by SunatServer (424)
insert into VERSION(id, status, records, created_at, updated_at, version)
values (1, 'COMPLETED', 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);

create table CONTRIBUYENTE_VERSION_1 partition of CONTRIBUYENTE for values from (1) TO (2);
