package io.searchpe.controller;

import io.searchpe.model.Company;

import javax.ws.rs.*;
import java.util.List;

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
