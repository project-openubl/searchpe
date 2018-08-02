package io.searchpe.controller;

import io.searchpe.model.Company;
import io.searchpe.model.Version;
import io.searchpe.services.CompanyService;
import io.searchpe.services.VersionService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Path("/companies")
public interface CompanyController {

    @GET
    @Path("/")
    @Produces("application/json")
    List<Company> getCompanies(
            @QueryParam("ruc") String ruc,
            @QueryParam("razonSocial") String razonSocial,
            @QueryParam("filterText") String filterText
    );

    @GET
    @Path("/{id}")
    @Produces("application/json")
    Company getCompanyById(@PathParam("id") Long id);
}
