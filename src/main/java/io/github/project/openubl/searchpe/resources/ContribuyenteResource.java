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
import javax.ws.rs.*;
import java.util.List;

@Path("/contribuyentes")
@ApplicationScoped
public class ContribuyenteResource {

    @GET
    @Path("/")
    @Produces("application/json")
    public PageRepresentation<ContribuyenteEntity> getContribuyentes(
            @QueryParam("filterText") String filterText,
            @QueryParam("offset") @DefaultValue("0") Integer offset,
            @QueryParam("limit") @DefaultValue("10") Integer limit,
            @QueryParam("sort_by") @DefaultValue("name") List<String> sortBy
    ) {
        VersionEntity version = VersionRepository.findActive().orElseThrow(BadRequestException::new);

        PageBean pageBean = ResourceUtils.getPageBean(offset, limit);
        List<SortBean> sortBeans = ResourceUtils.getSortBeans(sortBy, ContribuyenteRepository.SORT_BY_FIELDS);

        PageModel<ContribuyenteEntity> pageModel;
        if (filterText != null && !filterText.trim().isEmpty()) {
            pageModel = ContribuyenteRepository.list(version, filterText, pageBean, sortBeans);
        } else {
            pageModel = ContribuyenteRepository.list(version, pageBean, sortBeans);
        }

        PageRepresentation<ContribuyenteEntity> contribuyenteEntityPageRepresentation = EntityToRepresentation.toRepresentation(
                pageModel,
                versionEntity -> versionEntity
        );
        return contribuyenteEntityPageRepresentation;
    }

    @GET
    @Path("/{ruc}")
    @Produces("application/json")
    public ContribuyenteEntity getContribuyente(@PathParam("ruc") String ruc) {
        VersionEntity version = VersionRepository.findActive().orElseThrow(NotFoundException::new);
        return ContribuyenteRepository.findByRuc(version, ruc).orElseThrow(NotFoundException::new);
    }
}
