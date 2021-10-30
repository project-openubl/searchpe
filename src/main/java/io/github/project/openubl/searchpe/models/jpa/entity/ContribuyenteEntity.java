/*
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

import io.github.project.openubl.searchpe.models.TipoPersona;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.IdentifierBridgeRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Indexed
@Entity
@Table(name = "contribuyente")
public class ContribuyenteEntity extends PanacheEntityBase {

    @IndexedEmbedded(name = "embeddedId")
    @DocumentId(identifierBridge = @IdentifierBridgeRef(type = ContribuyenteIdBridge.class))
    @JsonbTransient
    @EmbeddedId
    public ContribuyenteId id;

    @GenericField
    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "tipo_persona")
    public TipoPersona tipoPersona;

    @Column(name = "numero_documento", insertable = false, updatable = false)
    public String numeroDocumento;

    @FullTextField(analyzer = "nombreAnalyser")
    @KeywordField(name = "nombre_sort", sortable = Sortable.YES, normalizer = "nombreSortNormalizer")
    @NotNull
    @Column(name = "nombre")
    public String nombre;

    @Column(name = "estado")
    public String estado;

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

    public static ContribuyenteEntity fullClone(ContribuyenteEntity entity) {
        return ContribuyenteEntity
                .Builder.aContribuyenteEntity()
                .withId(new ContribuyenteId(entity.id.versionId, entity.id.numeroDocumento))
                .withTipoPersona(entity.tipoPersona)
                .withNombre(entity.nombre)
                .withEstado(entity.estado)
                .withCondicionDomicilio(entity.condicionDomicilio)
                .withUbigeo(entity.ubigeo)
                .withTipoVia(entity.tipoVia)
                .withNombreVia(entity.nombreVia)
                .withCodigoZona(entity.codigoZona)
                .withTipoZona(entity.tipoZona)
                .withNumero(entity.numero)
                .withInterior(entity.interior)
                .withLote(entity.lote)
                .withDepartamento(entity.departamento)
                .withManzana(entity.manzana)
                .withKilometro(entity.kilometro)
                .build();
    }

    public static final class Builder {
        public ContribuyenteId id;
        public TipoPersona tipoPersona;
        public String nombre;
        public String estado;
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

        public Builder withTipoPersona(TipoPersona tipoPersona) {
            this.tipoPersona = tipoPersona;
            return this;
        }

        public Builder withNombre(String nombre) {
            this.nombre = nombre;
            return this;
        }

        public Builder withEstado(String estado) {
            this.estado = estado;
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
            contribuyenteEntity.tipoPersona = this.tipoPersona;
            contribuyenteEntity.id = this.id;
            contribuyenteEntity.lote = this.lote;
            contribuyenteEntity.nombre = this.nombre;
            contribuyenteEntity.condicionDomicilio = this.condicionDomicilio;
            contribuyenteEntity.tipoZona = this.tipoZona;
            contribuyenteEntity.interior = this.interior;
            contribuyenteEntity.estado = this.estado;
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
