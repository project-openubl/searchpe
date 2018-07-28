create sequence sequence start with 1 increment by 100;

    create table company (
       id bigint not null,
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

    create table version (
       id varchar(255) not null,
        complete char(255),
        date timestamp,
        number bigint,
        primary key (id)
    );

    create table version_metrics (
       version_id varchar(255) not null,
        value bigint,
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
