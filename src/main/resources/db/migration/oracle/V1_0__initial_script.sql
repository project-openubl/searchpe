create sequence sequence start with 1 increment by 50;

    create table company (
       id number(19,0) not null,
        codigo_zona varchar2(30 char),
        condicion_domicilio varchar2(30 char),
        departamento varchar2(30 char),
        estado_contribuyente varchar2(30 char),
        interior varchar2(30 char),
        kilometro varchar2(30 char),
        lote varchar2(30 char),
        manzana varchar2(30 char),
        nombre_via varchar2(100 char),
        numero varchar2(30 char),
        razon_social varchar2(255 char),
        ruc varchar2(11 char),
        tipo_via varchar2(30 char),
        tipo_zona varchar2(30 char),
        ubigeo varchar2(6 char),
        version varchar2(40 char),
        primary key (id)
    );

    create table version (
       id varchar2(40 char) not null,
        complete char(1 char),
        date timestamp,
        number number(19,0),
        primary key (id)
    );

    alter table company
       add constraint UKbp4h8nqno63ltev28my13t061 unique (version, ruc);

    alter table company
       add constraint FKe8jbnj2wkjv3di24a5da5mj0e
       foreign key (version)
       references version;
