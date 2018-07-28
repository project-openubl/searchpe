create sequence sequence start 1 increment 50;

    create table company (
       id int8 not null,
        codigo_zona varchar(10),
        condicion_domicilio varchar(20),
        departamento varchar(6),
        estado_contribuyente varchar(20),
        interior varchar(6),
        kilometro varchar(6),
        lote varchar(6),
        manzana varchar(6),
        nombre_via varchar(100),
        numero varchar(6),
        razon_social varchar(255),
        ruc varchar(11),
        tipo_via varchar(5),
        tipo_zona varchar(10),
        ubigeo varchar(6),
        version varchar(255),
        primary key (id)
    );

    create table version (
       id varchar(255) not null,
        complete char(1),
        date timestamp,
        number int8,
        primary key (id)
    );

    create table version_metrics (
       version_id varchar(255) not null,
        value int8,
        name varchar(255) not null,
        primary key (version_id, name)
    );

    alter table company
       add constraint UKbp4h8nqno63ltev28my13t061 unique (version, ruc);

    alter table company
       add constraint FKe8jbnj2wkjv3di24a5da5mj0e
       foreign key (version)
       references version;

    alter table version_metrics
       add constraint FKna6siv7x5ipel6gywm739s7qt
       foreign key (version_id)
       references version;
