/**
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Eclipse Public License - v 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.searchpe.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Entity
@Table(name = "contribuyente")
public class ContribuyenteEntity extends PanacheEntityBase {

    @Id
    public String id;

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

    @JsonbTransient
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", foreignKey = @ForeignKey)
    public VersionEntity version;

    public static final class Builder {
        public String id;
        public String ruc;
        public String razonSocial;
        public String estadoContribuyente;
        public String condicionDomicilio;
        public String ubigeo;
        public String tipoVia;
        public String nombreVia;
        public String codigoZona;
        public String tipoZona;
        public String numero;
        public String interior;
        public String lote;
        public String departamento;
        public String manzana;
        public String kilometro;
        public VersionEntity version;

        private Builder() {
        }

        public static Builder aContribuyenteEntity() {
            return new Builder();
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withRuc(String ruc) {
            this.ruc = ruc;
            return this;
        }

        public Builder withRazonSocial(String razonSocial) {
            this.razonSocial = razonSocial;
            return this;
        }

        public Builder withEstadoContribuyente(String estadoContribuyente) {
            this.estadoContribuyente = estadoContribuyente;
            return this;
        }

        public Builder withCondicionDomicilio(String condicionDomicilio) {
            this.condicionDomicilio = condicionDomicilio;
            return this;
        }

        public Builder withUbigeo(String ubigeo) {
            this.ubigeo = ubigeo;
            return this;
        }

        public Builder withTipoVia(String tipoVia) {
            this.tipoVia = tipoVia;
            return this;
        }

        public Builder withNombreVia(String nombreVia) {
            this.nombreVia = nombreVia;
            return this;
        }

        public Builder withCodigoZona(String codigoZona) {
            this.codigoZona = codigoZona;
            return this;
        }

        public Builder withTipoZona(String tipoZona) {
            this.tipoZona = tipoZona;
            return this;
        }

        public Builder withNumero(String numero) {
            this.numero = numero;
            return this;
        }

        public Builder withInterior(String interior) {
            this.interior = interior;
            return this;
        }

        public Builder withLote(String lote) {
            this.lote = lote;
            return this;
        }

        public Builder withDepartamento(String departamento) {
            this.departamento = departamento;
            return this;
        }

        public Builder withManzana(String manzana) {
            this.manzana = manzana;
            return this;
        }

        public Builder withKilometro(String kilometro) {
            this.kilometro = kilometro;
            return this;
        }

        public Builder withVersion(VersionEntity version) {
            this.version = version;
            return this;
        }

        public ContribuyenteEntity build() {
            ContribuyenteEntity contribuyenteEntity = new ContribuyenteEntity();
            contribuyenteEntity.ruc = this.ruc;
            contribuyenteEntity.estadoContribuyente = this.estadoContribuyente;
            contribuyenteEntity.tipoVia = this.tipoVia;
            contribuyenteEntity.manzana = this.manzana;
            contribuyenteEntity.tipoZona = this.tipoZona;
            contribuyenteEntity.id = this.id;
            contribuyenteEntity.condicionDomicilio = this.condicionDomicilio;
            contribuyenteEntity.ubigeo = this.ubigeo;
            contribuyenteEntity.lote = this.lote;
            contribuyenteEntity.version = this.version;
            contribuyenteEntity.interior = this.interior;
            contribuyenteEntity.kilometro = this.kilometro;
            contribuyenteEntity.razonSocial = this.razonSocial;
            contribuyenteEntity.departamento = this.departamento;
            contribuyenteEntity.nombreVia = this.nombreVia;
            contribuyenteEntity.codigoZona = this.codigoZona;
            contribuyenteEntity.numero = this.numero;
            return contribuyenteEntity;
        }
    }
}
