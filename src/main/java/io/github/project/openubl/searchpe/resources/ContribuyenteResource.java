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
package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.models.PageBean;
import io.github.project.openubl.searchpe.models.PageModel;
import io.github.project.openubl.searchpe.models.PageRepresentation;
import io.github.project.openubl.searchpe.models.SortBean;
import io.github.project.openubl.searchpe.models.jpa.ContribuyenteRepository;
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.utils.EntityToRepresentation;
import io.github.project.openubl.searchpe.utils.ResourceUtils;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
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

    @GET
    @Path("/")
    @Produces("application/json")
    public Response getContribuyentes(
            @QueryParam("filterText") String filterText,
            @QueryParam("offset") @DefaultValue("0") Integer offset,
            @QueryParam("limit") @DefaultValue("10") Integer limit,
            @QueryParam("sort_by") @DefaultValue("name") List<String> sortBy
    ) {
        Optional<VersionEntity> versionOptional = versionRepository.findActive();
        if (versionOptional.isEmpty()) {
            return Response.noContent().build();
        }

        VersionEntity version = versionOptional.get();

        PageBean pageBean = ResourceUtils.getPageBean(offset, limit);
        List<SortBean> sortBeans = ResourceUtils.getSortBeans(sortBy, ContribuyenteRepository.SORT_BY_FIELDS);

        PageModel<ContribuyenteEntity> pageModel;
        if (filterText != null && !filterText.trim().isEmpty()) {
            pageModel = contribuyenteRepository.list(version, filterText, pageBean, sortBeans);
        } else {
            pageModel = contribuyenteRepository.list(version, pageBean, sortBeans);
        }

        PageRepresentation<ContribuyenteEntity> result = EntityToRepresentation.toRepresentation(
                pageModel,
                versionEntity -> versionEntity
        );
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @GET
    @Path("/{ruc}")
    @Produces("application/json")
    public ContribuyenteEntity getContribuyente(@PathParam("ruc") String ruc) {
        VersionEntity version = versionRepository.findActive().orElseThrow(NotFoundException::new);
        return contribuyenteRepository.findByRuc(version, ruc).orElseThrow(NotFoundException::new);
    }
}
