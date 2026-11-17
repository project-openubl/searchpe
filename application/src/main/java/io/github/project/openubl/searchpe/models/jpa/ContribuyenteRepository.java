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
package io.github.project.openubl.searchpe.models.jpa;

import io.github.project.openubl.searchpe.models.FilterBean;
import io.github.project.openubl.searchpe.models.PageBean;
import io.github.project.openubl.searchpe.models.SearchResultBean;
import io.github.project.openubl.searchpe.models.SortBean;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteId;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.utils.DataHelper;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Transactional
@ApplicationScoped
public class ContribuyenteRepository implements PanacheRepositoryBase<ContribuyenteEntity, ContribuyenteId> {

    public static final String[] SORT_BY_FIELDS = {"nombre"};

    public Optional<ContribuyenteEntity> findByRuc(VersionEntity version, String ruc) {
        Parameters parameters = Parameters
                .with("versionId", version.id)
                .and("ruc", ruc);
        return VersionEntity.find("From ContribuyenteEntity as c where c.id.versionId = :versionId and c.id.ruc = :ruc", parameters)
                .singleResultOptional();
    }

    public Optional<ContribuyenteEntity> findByDni(VersionEntity version, String dni) {
        return DataHelper.getRucFromDni(dni).flatMap(ruc -> findByRuc(version, ruc));
    }

    public SearchResultBean<ContribuyenteEntity> list(VersionEntity version, FilterBean filterBean, PageBean pageBean, List<SortBean> sortBy) {
        Sort sort = Sort.by();
        sortBy.forEach(f -> sort.and(f.getFieldName(), f.isAsc() ? Sort.Direction.Ascending : Sort.Direction.Descending));

        StringBuilder queryBuilder = new StringBuilder("From ContribuyenteEntity as c where ");
        Parameters parameters = Parameters.with("versionId", version.id);

        List<String> queryConditions = new ArrayList<>();

        if (filterBean.getTipoPersona() != null) {
            // SUNAT RUC prefixes: 10 is a natural person with a DNI (dni is derived from it), 20 is a legal entity.
            // Other prefixes (15, 17, ...) match neither filter.
            queryConditions.add(switch (filterBean.getTipoPersona()) {
                case NATURAL -> "c.dni is not null";
                case JURIDICA -> {
                    parameters.and("rucPrefix", DataHelper.PERSONA_JURIDICA_RUC_PREFIX + "%");
                    yield "c.id.ruc like :rucPrefix";
                }
            });
        }

        queryConditions.add("c.id.versionId = :versionId");

        if (filterBean.getFilterText() != null && !filterBean.getFilterText().isBlank()) {
            queryConditions.add("lower(c.nombre) like :filterText");
            parameters.and("filterText", "%" + filterBean.getFilterText().toLowerCase() + "%");
        }

        queryBuilder.append(String.join(" and ", queryConditions));

        PanacheQuery<ContribuyenteEntity> query = VersionEntity
                .find(queryBuilder.toString(), sort, parameters)
                .range(pageBean.getOffset(), pageBean.getOffset() + pageBean.getLimit() - 1);

        return SearchResultBean.<ContribuyenteEntity>builder()
                .offset(pageBean.getOffset())
                .limit(pageBean.getLimit())
                .totalElements(query.count())
                .pageElements(query.list())
                .build();
    }

}
