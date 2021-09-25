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
package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.models.PageBean;
import io.github.project.openubl.searchpe.models.PageRepresentation;
import io.github.project.openubl.searchpe.models.SortBean;
import io.github.project.openubl.searchpe.models.jpa.ContribuyenteRepository;
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteId;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.utils.ResourceUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.query.dsl.SearchQueryOptionsStep;
import org.hibernate.search.engine.search.sort.SearchSort;
import org.hibernate.search.engine.search.sort.dsl.CompositeSortComponentsStep;
import org.hibernate.search.engine.search.sort.dsl.SortOrder;
import org.hibernate.search.mapper.orm.scope.SearchScope;
import org.hibernate.search.mapper.orm.search.loading.dsl.SearchLoadingOptionsStep;
import org.hibernate.search.mapper.orm.session.SearchSession;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.constraints.Max;
import javax.ws.rs.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Transactional
@ApplicationScoped
@Path("/contribuyentes")
public class ContribuyenteResource {

    @Inject
    VersionRepository versionRepository;

    @Inject
    ContribuyenteRepository contribuyenteRepository;

    @Inject
    SearchSession searchSession;

    @Operation(summary = "Search contribuyentes", description = "Get contribuyentes in a page")
    @GET
    @Path("/")
    @Produces("application/json")
    public PageRepresentation<ContribuyenteEntity> getContribuyentes(
            @QueryParam("filterText") String filterText,
            @QueryParam("offset") @DefaultValue("0") @Max(9_000) Integer offset,
            @QueryParam("limit") @DefaultValue("10") @Max(1_000) Integer limit,
            @QueryParam("sort_by") @DefaultValue("name") List<String> sortBy
    ) {
        Optional<VersionEntity> versionOptional = versionRepository.findActive();
        if (versionOptional.isEmpty()) {
            PageRepresentation<ContribuyenteEntity> result = new PageRepresentation<>();

            PageRepresentation.Meta meta = new PageRepresentation.Meta();
            meta.setOffset(offset);
            meta.setLimit(limit);
            meta.setCount(0L);

            result.setMeta(meta);
            result.setData(Collections.emptyList());

            return result;
        }
        VersionEntity version = versionOptional.get();

        PageBean pageBean = ResourceUtils.getPageBean(offset, limit);
        List<SortBean> sortBeans = ResourceUtils.getSortBeans(sortBy, ContribuyenteRepository.SORT_BY_FIELDS);

        SearchSort searchSort = null;
        if (!sortBeans.isEmpty()) {
            SearchScope<ContribuyenteEntity> searchScope = searchSession.scope(ContribuyenteEntity.class);

            CompositeSortComponentsStep<?> compositeSortComponents = searchScope.sort().composite();
            sortBeans.stream()
                    .map(f -> searchScope.sort()
                            .field(f.getFieldName() + "_sort")
                            .order(f.isAsc() ? SortOrder.ASC : SortOrder.DESC)
                            .toSort())
                    .forEach(compositeSortComponents::add);

            searchSort = compositeSortComponents.toSort();
        }

        SearchQueryOptionsStep<?, ContribuyenteEntity, SearchLoadingOptionsStep, ?, ?> searchQuery;
        if (filterText != null && !filterText.trim().isEmpty()) {
            searchQuery = searchSession.search(ContribuyenteEntity.class)
                    .where(f -> f.bool()
                            .must(f.match().field("embeddedId.versionId").matching(version.id))
                            .must(f.match().fields("razonSocial").matching(filterText))
                    );
        } else {
            searchQuery = searchSession.search(ContribuyenteEntity.class)
                    .where(f -> f.match().field("embeddedId.versionId").matching(version.id));
        }

        if (searchSort != null) {
            searchQuery = searchQuery.sort(searchSort);
        }
        SearchResult<ContribuyenteEntity> searchResult = searchQuery.fetch(offset, limit);

        PageRepresentation.Meta meta = new PageRepresentation.Meta();
        meta.setOffset(pageBean.getOffset());
        meta.setLimit(pageBean.getLimit());
        meta.setCount(searchResult.total().hitCount());

        PageRepresentation<ContribuyenteEntity> result = new PageRepresentation<>();
        result.setMeta(meta);
        result.setData(searchResult.hits());

        return result;
    }

    @Operation(summary = "Get contribuyente by RUC", description = "Get contribuyentes by RUC")
    @GET
    @Path("/{ruc}")
    @Produces("application/json")
    public ContribuyenteEntity getContribuyente(@PathParam("ruc") String ruc) {
        VersionEntity version = versionRepository.findActive().orElseThrow(NotFoundException::new);
        return contribuyenteRepository.findByIdOptional(new ContribuyenteId(version.id, ruc)).orElseThrow(NotFoundException::new);
    }
}
