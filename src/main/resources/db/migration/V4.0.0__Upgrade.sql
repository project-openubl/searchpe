-- Version
alter table VERSION
alter
column id type int8;

alter table VERSION
alter
column version type int4;

alter table if exists VERSION add column trigger_key varchar (255) null;

-- Basic user
alter table BASIC_USER
alter
column version type int4;

-- Contribuyente
alter table CONTRIBUYENTE
alter
column version_id type int8;

alter table if exists CONTRIBUYENTE rename column numero_documento to ruc;
alter table if exists CONTRIBUYENTE add column dni varchar (8) null;
alter table if exists CONTRIBUYENTE drop tipo_persona;

-- create index contribuyente_version_fkey on public.contribuyente (version_id);
-- create index contribuyente_dni_index on contribuyente using btree (version_id, dni);
-- create index contribuyente_nombre_index on contribuyente using btree (version_id, nombre);

create index contribuyente_dni_index on contribuyente using hash (dni);
