-- Delete all preexisting SUNAT data
delete from CONTRIBUYENTE;
delete from VERSION;

-- Alter tables to match new Models
alter table if exists VERSION add column version int8 not null;
alter table if exists CONTRIBUYENTE add column tipo_contribuyente varchar (15) not null;

-- Create new table for Basic Auth support
CREATE TABLE BASIC_USER
(
    id       int8         NOT NULL,
    username VARCHAR(250) NOT NULL,
    password VARCHAR(250) NOT NULL,
    role     VARCHAR(250),
    version  int8         NOT NULL,
    PRIMARY KEY (id)
);