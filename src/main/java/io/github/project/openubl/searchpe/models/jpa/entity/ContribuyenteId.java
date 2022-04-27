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

import lombok.Builder;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;

@Builder
@Embeddable
public class ContribuyenteId implements Serializable {

    @GenericField(name = "versionId")
    @Column(name = "version_id")
    @NotNull
    public Long versionId;

    @Size(min = 11, max = 11)
    @NotNull
    @Column(name = "numero_documento")
    public String numeroDocumento;

    public ContribuyenteId() {
    }

    public ContribuyenteId(Long versionId, String numeroDocumento) {
        this.versionId = versionId;
        this.numeroDocumento = numeroDocumento;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContribuyenteId that = (ContribuyenteId) o;
        return versionId.equals(that.versionId) && numeroDocumento.equals(that.numeroDocumento);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, numeroDocumento);
    }

}
