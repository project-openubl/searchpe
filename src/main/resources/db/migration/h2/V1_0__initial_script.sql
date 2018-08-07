create sequence sequence start with 1 increment by 50;

    create table company (
       id bigint not null,
        codigo_zona varchar(30),
        condicion_domicilio varchar(30),
        departamento varchar(30),
        estado_contribuyente varchar(30),
        interior varchar(30),
        kilometro varchar(30),
        lote varchar(30),
        manzana varchar(30),
        nombre_via varchar(100),
        numero varchar(30),
        razon_social varchar(255),
        ruc varchar(11),
        tipo_via varchar(30),
        tipo_zona varchar(30),
        ubigeo varchar(6),
        version varchar(40),
        primary key (id)
    );

    create table version (
       id varchar(40) not null,
        complete char(255),
        date timestamp,
        number bigint,
        primary key (id)
    );

    create table version_metrics (
       version_id varchar(40) not null,
        value bigint,
        name varchar(100) not null,
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
