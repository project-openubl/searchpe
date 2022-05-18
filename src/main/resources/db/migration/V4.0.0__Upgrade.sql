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
drop table if exists CONTRIBUYENTE;
create table CONTRIBUYENTE
(
    version_id          int8         NOT NULL,
    ruc                 VARCHAR(11)  NOT NULL,
    dni                 VARCHAR(8)   NULL,
    nombre              VARCHAR(150) NOT NULL,
    estado              VARCHAR(30),
    condicion_domicilio VARCHAR(30),
    ubigeo              VARCHAR(6),
    tipo_via            VARCHAR(30),
    nombre_via          VARCHAR(100),
    codigo_zona         VARCHAR(30),
    tipo_zona           VARCHAR(30),
    numero              VARCHAR(30),
    interior            VARCHAR(30),
    lote                VARCHAR(30),
    departamento        VARCHAR(30),
    manzana             VARCHAR(30),
    kilometro           VARCHAR(30)
--     PRIMARY KEY (version_id, ruc)
) PARTITION BY HASH (version_id);

-- alter table CONTRIBUYENTE
--     add constraint fk_contribuyente_version foreign key (version_id) references VERSION;

create index contribuyente_ruc_index on contribuyente using hash (ruc);
create index contribuyente_dni_index on contribuyente using hash (dni);

create table CONTRIBUYENTE_1 partition of CONTRIBUYENTE for values with (modulus 3, remainder 0);
create table CONTRIBUYENTE_2 partition of CONTRIBUYENTE for values with (modulus 3, remainder 1);
create table CONTRIBUYENTE_3 partition of CONTRIBUYENTE for values with (modulus 3, remainder 2);
