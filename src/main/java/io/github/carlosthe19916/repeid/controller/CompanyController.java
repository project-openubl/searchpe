package io.github.carlosthe19916.repeid.controller;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.model.Version;
import io.github.carlosthe19916.repeid.services.CompanyService;
import io.github.carlosthe19916.repeid.services.VersionService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    public List<Company> getCompanies(
            @QueryParam("ruc") String ruc,
            @QueryParam("razonSocial") String razonSocial,
            @QueryParam("filterText") String filterText
    ) {
        Version lastVersion = versionService.getLastVersion().orElseThrow(NotFoundException::new);

        if (ruc != null) {
            Optional<Company> company = companyService.getCompanyByRuc(lastVersion, ruc);
            List<Company> companies = new ArrayList<>();
            company.ifPresent(companies::add);
            return companies;
        } else if (razonSocial != null) {
            return companyService.getCompanyByRazonSocial(lastVersion, razonSocial);
        } else if (filterText != null) {
            return companyService.getCompanyByFilterText(lastVersion, filterText);
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
