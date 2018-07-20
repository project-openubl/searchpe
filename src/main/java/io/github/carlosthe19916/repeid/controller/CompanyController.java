package io.github.carlosthe19916.repeid.controller;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.services.CompanyService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.*;

@Path("/companies")
@ApplicationScoped
public class CompanyController {

    @Inject
    private CompanyService companyService;

    @GET
    @Path("/{ruc}")
    @Produces("application/json")
    public Company getCompany(@PathParam("ruc") String ruc) {
        return companyService.getCompanyByRuc(ruc).orElseThrow(NotFoundException::new);
    }

}
