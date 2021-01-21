CREATE TABLE VERSION
(
    id         int8         NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    status     VARCHAR(50) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE CONTRIBUYENTE
(
    version_id           int8  NOT NULL,
    ruc                  VARCHAR(11)  NOT NULL,
    razon_social         VARCHAR(255) NOT NULL,
    estado_contribuyente VARCHAR(30),
    condicion_domicilio  VARCHAR(30),
    ubigeo               VARCHAR(6),
    tipo_via             VARCHAR(30),
    nombre_via           VARCHAR(100),
    codigo_zona          VARCHAR(30),
    tipo_zona            VARCHAR(30),
    numero               VARCHAR(30),
    interior             VARCHAR(30),
    lote                 VARCHAR(30),
    departamento         VARCHAR(30),
    manzana              VARCHAR(30),
    kilometro            VARCHAR(30),
    PRIMARY KEY (version_id, ruc)
);

ALTER TABLE CONTRIBUYENTE ADD CONSTRAINT fk_contribuyente_version FOREIGN KEY (version_id) REFERENCES VERSION;

COMMIT;
