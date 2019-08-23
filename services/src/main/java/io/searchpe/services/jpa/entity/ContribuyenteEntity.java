package io.searchpe.services.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Entity
@Table(name = "contribuyente", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"version_id", "ruc"})
})
public class ContribuyenteEntity extends PanacheEntity {

    @Size(min = 11, max = 11)
    @NotNull
    @Column(name = "ruc")
    public String ruc;

    @Size(max = 255)
    @NotNull
    @Column(name = "razon_social")
    public String razonSocial;

    @Size(max = 30)
    @Column(name = "estado_contribuyente")
    public String estadoContribuyente;

    @Size(max = 30)
    @Column(name = "condicion_domicilio")
    public String condicionDomicilio;

    @Size(min = 6, max = 6)
    @Column(name = "ubigeo")
    public String ubigeo;

    @Size(max = 30)
    @Column(name = "tipo_via")
    public String tipoVia;

    @Size(max = 100)
    @Column(name = "nombre_via")
    public String nombreVia;

    @Size(max = 30)
    @Column(name = "codigo_zona")
    public String codigoZona;

    @Size(max = 30)
    @Column(name = "tipo_zona")
    public String tipoZona;

    @Size(max = 30)
    @Column(name = "numero")
    public String numero;

    @Size(max = 30)
    @Column(name = "interior")
    public String interior;

    @Size(max = 30)
    @Column(name = "lote")
    public String lote;

    @Size(max = 30)
    @Column(name = "departamento")
    public String departamento;

    @Size(max = 30)
    @Column(name = "manzana")
    public String manzana;

    @Size(max = 30)
    @Column(name = "kilometro")
    public String kilometro;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", foreignKey = @ForeignKey)
    public VersionEntity version;

}
