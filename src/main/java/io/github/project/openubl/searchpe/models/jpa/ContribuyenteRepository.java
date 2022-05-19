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
import io.github.project.openubl.searchpe.models.TipoPersona;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteId;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.query.dsl.SearchQueryOptionsStep;
import org.hibernate.search.engine.search.sort.SearchSort;
import org.hibernate.search.engine.search.sort.dsl.CompositeSortComponentsStep;
import org.hibernate.search.engine.search.sort.dsl.SortOrder;
import org.hibernate.search.mapper.orm.scope.SearchScope;
import org.hibernate.search.mapper.orm.search.loading.dsl.SearchLoadingOptionsStep;
import org.hibernate.search.mapper.orm.session.SearchSession;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Transactional
@ApplicationScoped
public class ContribuyenteRepository implements PanacheRepositoryBase<ContribuyenteEntity, ContribuyenteId> {

    public static final String[] SORT_BY_FIELDS = {"nombre"};

    @Inject
    Instance<SearchSession> searchSession;

    private Optional<SearchSession> getSearchSession() {
        for (SearchSession session : searchSession) {
            return Optional.of(session);
        }
        return Optional.empty();
    }

    public Optional<ContribuyenteEntity> findByRuc(VersionEntity version, String ruc) {
        Parameters parameters = Parameters
                .with("versionId", version.id)
                .and("ruc", ruc);
        return VersionEntity.find("From ContribuyenteEntity as c where c.id.versionId = :versionId and c.id.ruc = :ruc", parameters)
                .singleResultOptional();
    }

    public Optional<ContribuyenteEntity> findByDni(VersionEntity version, String dni) {
        Parameters parameters = Parameters
                .with("versionId", version.id)
                .and("ruc", IntStream.range(1, 10).mapToObj(operand -> "10" + dni + operand).collect(Collectors.toList()));
        return VersionEntity.find("From ContribuyenteEntity as c where c.id.versionId = :versionId and ruc in :ruc", parameters)
                .<ContribuyenteEntity>list()
                .stream().filter(contribuyente -> contribuyente.getDni().equals(dni))
                .findFirst();
    }

    public SearchResultBean<ContribuyenteEntity> list(VersionEntity version, FilterBean filterBean, PageBean pageBean, List<SortBean> sortBy) {
        Sort sort = Sort.by();
        sortBy.forEach(f -> sort.and(f.getFieldName(), f.isAsc() ? Sort.Direction.Ascending : Sort.Direction.Descending));

        StringBuilder queryBuilder = new StringBuilder("From ContribuyenteEntity as c where ");
        Parameters parameters = Parameters.with("versionId", version.id);

        List<String> queryConditions = new ArrayList<>();

        if (filterBean.getTipoPersona() != null) {
            if (filterBean.getTipoPersona().equals(TipoPersona.JURIDICA)) {
                queryConditions.add("c.id.ruc is not null");
            }
            if (filterBean.getTipoPersona().equals(TipoPersona.NATURAL)) {
                queryConditions.add("c.dni is not null");
            }
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

    public SearchResultBean<ContribuyenteEntity> listES(VersionEntity version, FilterBean filterBean, PageBean pageBean, List<SortBean> sortBy) {
        SearchSession searchSession = getSearchSession().orElseThrow(() -> new IllegalStateException("Could not find a SearchSession available"));
        SearchSort searchSort = null;
        if (!sortBy.isEmpty()) {
            SearchScope<ContribuyenteEntity> searchScope = searchSession.scope(ContribuyenteEntity.class);

            CompositeSortComponentsStep<?> compositeSortComponents = searchScope.sort().composite();
            sortBy.stream()
                    .map(f -> searchScope.sort()
                            .field(f.getFieldName() + "_sort")
                            .order(f.isAsc() ? SortOrder.ASC : SortOrder.DESC)
                            .toSort())
                    .forEach(compositeSortComponents::add);

            searchSort = compositeSortComponents.toSort();
        }

        SearchQueryOptionsStep<?, ContribuyenteEntity, SearchLoadingOptionsStep, ?, ?> searchQuery = searchSession.search(ContribuyenteEntity.class)
                .where(f -> {
                    BooleanPredicateClausesStep<?> result = f.bool();
                    result = result.must(f.match().field("embeddedId.versionId").matching(version.id));
                    if (filterBean.getTipoPersona() != null) {
                        if (filterBean.getTipoPersona().equals(TipoPersona.JURIDICA)) {
                            result = result.must(f.exists().field("ruc"));
                        }
                        if (filterBean.getTipoPersona().equals(TipoPersona.NATURAL)) {
                            result = result.must(f.exists().field("dni"));
                        }
                    }
                    if (filterBean.getFilterText() != null && !filterBean.getFilterText().trim().isEmpty()) {
                        result = result.must(f.match().fields("nombre").matching(filterBean.getFilterText()));
                    }
                    return result;
                });

        if (searchSort != null) {
            searchQuery = searchQuery.sort(searchSort);
        }
        SearchResult<ContribuyenteEntity> searchResult = searchQuery.fetch(pageBean.getOffset(), pageBean.getLimit());

        return SearchResultBean.<ContribuyenteEntity>builder()
                .offset(pageBean.getOffset())
                .limit(pageBean.getLimit())
                .totalElements(searchResult.total().hitCount())
                .pageElements(searchResult.hits())
                .build();
    }
}
