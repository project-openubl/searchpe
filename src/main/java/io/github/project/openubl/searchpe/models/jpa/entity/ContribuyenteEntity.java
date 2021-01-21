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
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "contribuyente")
public class ContribuyenteEntity extends PanacheEntityBase {

    @JsonbTransient
    @EmbeddedId
    public ContribuyenteId id;

    @Column(name = "ruc", insertable = false, updatable = false)
    public String ruc;

    @NotNull
    @Column(name = "razon_social")
    public String razonSocial;

    @Column(name = "estado_contribuyente")
    public String estadoContribuyente;

    @Column(name = "condicion_domicilio")
    public String condicionDomicilio;

    @Column(name = "ubigeo")
    public String ubigeo;

    @Column(name = "tipo_via")
    public String tipoVia;

    @Column(name = "nombre_via")
    public String nombreVia;

    @Column(name = "codigo_zona")
    public String codigoZona;

    @Column(name = "tipo_zona")
    public String tipoZona;

    @Column(name = "numero")
    public String numero;

    @Column(name = "interior")
    public String interior;

    @Column(name = "lote")
    public String lote;

    @Column(name = "departamento")
    public String departamento;

    @Column(name = "manzana")
    public String manzana;

    @Column(name = "kilometro")
    public String kilometro;

    public static final class Builder {
        public ContribuyenteId id;
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

        private Builder() {
        }

        public static Builder aContribuyenteEntity() {
            return new Builder();
        }

        public Builder withId(ContribuyenteId id) {
            this.id = id;
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

        public ContribuyenteEntity build() {
            ContribuyenteEntity contribuyenteEntity = new ContribuyenteEntity();
            contribuyenteEntity.manzana = this.manzana;
            contribuyenteEntity.id = this.id;
            contribuyenteEntity.lote = this.lote;
            contribuyenteEntity.razonSocial = this.razonSocial;
            contribuyenteEntity.condicionDomicilio = this.condicionDomicilio;
            contribuyenteEntity.tipoZona = this.tipoZona;
            contribuyenteEntity.interior = this.interior;
            contribuyenteEntity.estadoContribuyente = this.estadoContribuyente;
            contribuyenteEntity.ubigeo = this.ubigeo;
            contribuyenteEntity.kilometro = this.kilometro;
            contribuyenteEntity.codigoZona = this.codigoZona;
            contribuyenteEntity.departamento = this.departamento;
            contribuyenteEntity.numero = this.numero;
            contribuyenteEntity.tipoVia = this.tipoVia;
            contribuyenteEntity.nombreVia = this.nombreVia;
            return contribuyenteEntity;
        }
    }
}
