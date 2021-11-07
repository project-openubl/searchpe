-- Delete all preexisting SUNAT data
delete
from CONTRIBUYENTE;
delete
from VERSION;

-- Alter tables to match new Models
alter table if exists VERSION add column version int8 not null;

alter table if exists CONTRIBUYENTE add column tipo_persona varchar (15) not null;
alter table if exists CONTRIBUYENTE rename column ruc to numero_documento;
alter table if exists CONTRIBUYENTE rename column razon_social to nombre;
alter table if exists CONTRIBUYENTE rename column estado_contribuyente to estado;

-- Create new table for Basic Auth support
CREATE TABLE BASIC_USER
(
    id          int8         NOT NULL,
    full_name   VARCHAR(250) NOT NULL,
    username    VARCHAR(250) NOT NULL,
    password    VARCHAR(250) NOT NULL,
    permissions VARCHAR(250),
    version     int8         NOT NULL,
    PRIMARY KEY (id)
);

alter table BASIC_USER
    add constraint UK4hs3cb8j320vu5apl2fb06dde unique (username);
