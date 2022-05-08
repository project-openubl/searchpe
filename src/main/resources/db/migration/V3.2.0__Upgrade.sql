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
