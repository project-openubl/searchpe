package io.github.carlosthe19916.repeid.controller;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.model.Version;
import io.github.carlosthe19916.repeid.services.CompanyService;
import io.github.carlosthe19916.repeid.services.VersionService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;

@Path("/companies")
@ApplicationScoped
public class CompanyController {

    @Inject
    private VersionService versionService;

    @Inject
    private CompanyService companyService;

    @GET
    @Path("/")
    @Produces("application/json")
    public Company getCompanies(
            @QueryParam("ruc") String ruc
    ) {
        Version lastVersion = versionService.getLastVersion().orElseThrow(NotFoundException::new);

        if (ruc != null) {
            return companyService.getCompanyByRuc(lastVersion, ruc).orElseThrow(NotFoundException::new);
        }

        throw new BadRequestException();
    }

    @GET
    @Path("/{id}")
    @Produces("application/json")
    public Company getCompanyById(@PathParam("id") Long id) {
        return companyService.getCompany(id).orElseThrow(NotFoundException::new);
    }

}
