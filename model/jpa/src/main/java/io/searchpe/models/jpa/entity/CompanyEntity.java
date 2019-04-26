package io.searchpe.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Entity
@Table(name = "company", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"version", "ruc"})
})
public class CompanyEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "company-pooled-lo")
    @GenericGenerator(name = "company-pooled-lo", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator", parameters = {
            @org.hibernate.annotations.Parameter(name = "sequence_name", value = "sequence"),
            @org.hibernate.annotations.Parameter(name = "initial_value", value = "1"),
            @org.hibernate.annotations.Parameter(name = "increment_size", value = "50"),
            @org.hibernate.annotations.Parameter(name = "optimizer", value = "pooled-lo")
    })
    public Long id;

    @Size(min = 11, max = 11)
    @NotNull
    @Column(name = "ruc")
    public String ruc;

    @Size(max = 255)
    @NotNull
    @Column(name = "razon_social")
    public String razonSocial;

    @Size(max = 30)
    @NotNull
    @Column(name = "estado_contribuyente")
    public String estadoContribuyente;

    @Size(max = 30)
    @NotNull
    @Column(name = "condicion_domicilio")
    public String condicionDomicilio;

    @Size(min = 6, max = 6)
    @NotNull
    @Column(name = "ubigeo")
    public String ubigeo;

    @Size(max = 30)
    @NotNull
    @Column(name = "tipo_via")
    public String tipoVia;

    @Size(max = 100)
    @NotNull
    @Column(name = "nombre_via")
    public String nombreVia;

    @Size(max = 30)
    @NotNull
    @Column(name = "codigo_zona")
    public String codigoZona;

    @Size(max = 30)
    @NotNull
    @Column(name = "tipo_zona")
    public String tipoZona;

    @Size(max = 30)
    @NotNull
    @Column(name = "numero")
    public String numero;

    @Size(max = 30)
    @NotNull
    @Column(name = "interior")
    public String interior;

    @Size(max = 30)
    @NotNull
    @Column(name = "lote")
    public String lote;

    @Size(max = 30)
    @NotNull
    @Column(name = "departamento")
    public String departamento;

    @Size(max = 30)
    @NotNull
    @Column(name = "manzana")
    public String manzana;

    @Size(max = 30)
    @NotNull
    @Column(name = "kilometro")
    public String kilometro;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version", foreignKey = @ForeignKey)
    public VersionEntity version;

}
