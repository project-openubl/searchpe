-- Version
alter table VERSION
    alter
        column id type int8;

alter table VERSION
    alter
        column version type int4;

alter table if exists VERSION
    add column trigger_key varchar(255) null;

-- Basic user
alter table BASIC_USER
    alter
        column version type int4;

-- Contribuyente
drop sequence if exists hibernate_sequence_version;
create sequence hibernate_sequence_version start 1 increment 1;

drop table if exists CONTRIBUYENTE;
create table CONTRIBUYENTE
(
    version_id          int8         not null,
    ruc                 varchar(11)  not null,
    dni                 varchar(8)   null,
    nombre              varchar(150) not null,
    estado              varchar(30),
    condicion_domicilio varchar(30),
    ubigeo              varchar(6),
    tipo_via            varchar(30),
    nombre_via          varchar(100),
    codigo_zona         varchar(30),
    tipo_zona           varchar(30),
    numero              varchar(30),
    interior            varchar(30),
    lote                varchar(30),
    departamento        varchar(30),
    manzana             varchar(30),
    kilometro           varchar(30)
) partition by hash (version_id);

create index contribuyente_ruc_index on contribuyente using hash (ruc);
create index contribuyente_dni_index on contribuyente using hash (dni);

create table CONTRIBUYENTE_1 partition of CONTRIBUYENTE for values with (modulus 3, remainder 0);
create table CONTRIBUYENTE_2 partition of CONTRIBUYENTE for values with (modulus 3, remainder 1);
create table CONTRIBUYENTE_3 partition of CONTRIBUYENTE for values with (modulus 3, remainder 2);
