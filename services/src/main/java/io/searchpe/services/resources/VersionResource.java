package io.searchpe.services.resources;

import io.searchpe.services.jpa.entity.VersionEntity;
import io.searchpe.services.providers.VersionProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Optional;

@Transactional
@Path("/versions")
@ApplicationScoped
public class VersionResource {

    @Inject
    VersionProvider versionProvider;

    @GET
    @Path("/current")
    @Produces("application/json")
    public Response getActiveVersion() {
        Optional<VersionEntity> version = versionProvider.getActive();
        return ResourceUtils.getResponseFromOptionalVersion(version);
    }

    @GET
    @Path("/{id}")
    @Produces("application/json")
    public Response getVersionById(@PathParam("id") Long id) {
        Optional<VersionEntity> version = versionProvider.getById(id);
        return ResourceUtils.getResponseFromOptionalVersion(version);
    }

}
