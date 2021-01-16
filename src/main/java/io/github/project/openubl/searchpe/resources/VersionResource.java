package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.*;
import java.util.List;

@Path("/versions")
@ApplicationScoped
public class VersionResource {

    @GET
    @Path("/")
    @Produces("application/json")
    public List<VersionEntity> getVersions() {
        return VersionEntity.listAll();
    }

    @GET
    @Path("/{id}")
    @Produces("application/json")
    public VersionEntity getVersion(@PathParam("id") Long id) {
        VersionEntity version = VersionEntity.findById(id);
        if (version == null) {
            throw new NotFoundException("Version[id=" + id + "] does not exists");
        }

        return version;
    }
}
