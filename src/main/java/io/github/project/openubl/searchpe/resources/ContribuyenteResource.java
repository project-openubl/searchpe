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
package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.models.FilterBean;
import io.github.project.openubl.searchpe.models.PageBean;
import io.github.project.openubl.searchpe.models.PageModel;
import io.github.project.openubl.searchpe.models.PageRepresentation;
import io.github.project.openubl.searchpe.models.SortBean;
import io.github.project.openubl.searchpe.models.TipoPersona;
import io.github.project.openubl.searchpe.models.jpa.ContribuyenteRepository;
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteId;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.security.Permission;
import io.github.project.openubl.searchpe.utils.EntityToRepresentation;
import io.github.project.openubl.searchpe.utils.ResourceUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.query.dsl.SearchQueryOptionsStep;
import org.hibernate.search.engine.search.sort.SearchSort;
import org.hibernate.search.engine.search.sort.dsl.CompositeSortComponentsStep;
import org.hibernate.search.engine.search.sort.dsl.SortOrder;
import org.hibernate.search.mapper.orm.scope.SearchScope;
import org.hibernate.search.mapper.orm.search.loading.dsl.SearchLoadingOptionsStep;
import org.hibernate.search.mapper.orm.session.SearchSession;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.constraints.Max;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Transactional
@ApplicationScoped
@Path("/contribuyentes")
public class ContribuyenteResource {

    @ConfigProperty(name = "quarkus.hibernate-search-orm.enabled")
    Optional<Boolean> isESEnabled;

    @Inject
    VersionRepository versionRepository;

    @Inject
    ContribuyenteRepository contribuyenteRepository;

    @RolesAllowed({Permission.admin, Permission.search})
    @Operation(summary = "Search contribuyentes", description = "Get contribuyentes in a page")
    @GET
    @Path("/")
    @Produces("application/json")
    @Counted(name = "searchContribuyenteChecks", description = "How many times the advanced search was used")
    @Timed(name = "searchContribuyenteTimer", description = "How long it took to serve the advanced search")
    public PageRepresentation<ContribuyenteEntity> getContribuyentes(
            @QueryParam("filterText") String filterText,
            @QueryParam("tipoContribuyente") String tipoPersona,
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

        FilterBean filterBean = new FilterBean();
        filterBean.setFilterText(filterText);
        filterBean.setTipoPersona(tipoPersona);

        PageModel<ContribuyenteEntity> list;
        if (!isESEnabled.orElse(false)) {
            list = contribuyenteRepository.list(version, filterBean, pageBean, sortBeans);
        } else {
            list = contribuyenteRepository.listES(version, filterBean, pageBean, sortBeans);
        }
        return EntityToRepresentation.toRepresentation(list, entity -> entity);
    }

    @RolesAllowed({Permission.admin, Permission.search})
    @Operation(summary = "Get contribuyente by numeroDocumento", description = "Get contribuyentes by numeroDocumento")
    @GET
    @Path("/{numeroDocumento}")
    @Produces("application/json")
    @Counted(name = "getContribuyenteChecks", description = "How many times the endpoint was used")
    @Timed(name = "getContribuyenteTimer", description = "How long it took to serve the data")
    public ContribuyenteEntity getContribuyente(@PathParam("numeroDocumento") String numeroDocumento) {
        VersionEntity version = versionRepository.findActive().orElseThrow(NotFoundException::new);
        return contribuyenteRepository.findByIdOptional(new ContribuyenteId(version.id, numeroDocumento)).orElseThrow(NotFoundException::new);
    }
}
