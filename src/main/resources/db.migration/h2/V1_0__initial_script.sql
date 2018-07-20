create sequence hibernate_sequence start with 1 increment by 1;


    create table company (
       id bigint not null,
        codigoZona varchar(255),
        condicionDomicilio varchar(255),
        departamento varchar(255),
        estadoContribuyente varchar(255),
        interior varchar(255),
        kilometro varchar(255),
        lote varchar(255),
        manzana varchar(255),
        nombreVia varchar(255),
        numero varchar(255),
        razonSocial varchar(255),
        ruc varchar(255),
        tipoVia varchar(255),
        tipoZona varchar(255),
        ubigeo varchar(255),
        primary key (id)
    );
