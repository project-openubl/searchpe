package io.github.carlosthe19916.repeid.controller;

import io.github.carlosthe19916.repeid.model.Company;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Path("/companies")
public class CompanyController {

    @GET
    @Path("/greeting")
    @Produces("application/json")
    public Company greeting(@QueryParam("name") String name) {
        return new Company();
    }

}
