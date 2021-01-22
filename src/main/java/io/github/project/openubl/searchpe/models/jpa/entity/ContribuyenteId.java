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

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ContribuyenteId implements Serializable {

    @GenericField(name = "versionId")
    @Column(name = "version_id")
    @NotNull
    public Long versionId;

    @Size(min = 11, max = 11)
    @NotNull
    @Column(name = "ruc")
    public String ruc;

    public ContribuyenteId() {
    }

    public ContribuyenteId(Long versionId, String ruc) {
        this.versionId = versionId;
        this.ruc = ruc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContribuyenteId that = (ContribuyenteId) o;
        return versionId.equals(that.versionId) && ruc.equals(that.ruc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, ruc);
    }

}
