package io.searchpe.services.resources;

import io.quarkus.panache.common.Page;
import io.searchpe.services.jpa.entity.ContribuyenteEntity;
import io.searchpe.services.jpa.entity.VersionEntity;
import io.searchpe.services.models.SearchResultModel;
import io.searchpe.services.providers.ContribuyenteProvider;
import io.searchpe.services.providers.VersionProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Path("/contribuyentes")
@ApplicationScoped
public class ContribuyenteResource {

    @Inject
    VersionProvider versionProvider;

    @Inject
    ContribuyenteProvider contribuyenteProvider;

    @GET
    @Path("/")
    @Produces("application/json")
    public SearchResultModel<ContribuyenteEntity> getCompanies(
            @QueryParam("filterText") String filterText,
            @QueryParam("ruc") String ruc,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size
    ) {
        Optional<VersionEntity> activeVersion = versionProvider.getActive();
        if (!activeVersion.isPresent()) {
            return new SearchResultModel<>();
        }

        VersionEntity versionEntity = activeVersion.get();
        Page searchPage = new Page((page -1) * size, size);

        if (ruc != null) {
            Optional<ContribuyenteEntity> contribuyente = contribuyenteProvider.getByRuc(versionEntity.id, ruc);

            List<ContribuyenteEntity> content = contribuyente.map(Arrays::asList).orElse(Collections.emptyList());
            long totalElements = content.size();

            return new SearchResultModel<>(content, totalElements);
        } else if (filterText != null) {
            return contribuyenteProvider.searchByFilterText(versionEntity.id, filterText, searchPage);
        }

        return contribuyenteProvider.search(versionEntity.id, searchPage);
    }

    @GET
    @Path("/{id}")
    @Produces("application/json")
    public Response getById(@PathParam("id") Long id) {
        Optional<ContribuyenteEntity> contribuyente = contribuyenteProvider.getById(id);
        return ResourceUtils.getResponseFromOptionalVersion(contribuyente);
    }

}
