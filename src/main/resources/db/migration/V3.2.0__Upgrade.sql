ALTER TABLE VERSION
ALTER
COLUMN id TYPE int8;

ALTER TABLE VERSION
ALTER
COLUMN version TYPE int4;

ALTER TABLE BASIC_USER
ALTER
COLUMN version TYPE int4;

ALTER TABLE CONTRIBUYENTE
ALTER
COLUMN version_id TYPE int8;

-- Contribuyente table for allowing DNI be stored in a single table
alter table if exists CONTRIBUYENTE rename column numero_documento to ruc;
alter table if exists CONTRIBUYENTE add column dni varchar (8) null;
alter table if exists CONTRIBUYENTE drop tipo_persona;
