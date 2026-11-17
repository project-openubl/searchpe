alter sequence version_sequence restart with 1000;

insert into VERSION(id, status, records, created_at, updated_at, version)
values (1, 'COMPLETED', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);

create table CONTRIBUYENTE_VERSION_1 partition of CONTRIBUYENTE for values from (1) TO (2);

-- Index as built by imports before indexes moved to the partition
create index ix_contribuyente_ruc_version_1 on CONTRIBUYENTE using hash (ruc) where version_id = 1;
