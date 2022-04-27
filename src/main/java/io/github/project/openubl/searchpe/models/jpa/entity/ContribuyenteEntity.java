/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.IdentifierBridgeRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.DocumentId;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.util.Objects;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionEntity that = (VersionEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
