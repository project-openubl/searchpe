create sequence hibernate_sequence start 1 increment 1;

    create table company (
       id int8 not null,
        codigo_zona varchar(255),
        condicion_domicilio varchar(255),
        departamento varchar(255),
        estado_contribuyente varchar(255),
        interior varchar(255),
        kilometro varchar(255),
        lote varchar(255),
        manzana varchar(255),
        nombre_via varchar(255),
        numero varchar(255),
        razon_social varchar(255),
        ruc varchar(255),
        tipo_via varchar(255),
        tipo_zona varchar(255),
        ubigeo varchar(255),
        version varchar(255),
        primary key (id)
    );

    create table import (
       id varchar(255) not null,
        complete char(1),
        date timestamp,
        number int8,
        primary key (id)
    );

    alter table company
       add constraint UKbp4h8nqno63ltev28my13t061 unique (version, ruc);

    alter table company
       add constraint FKp01662g0ahyh41a21o7tq5uya
       foreign key (version)
       references import;
