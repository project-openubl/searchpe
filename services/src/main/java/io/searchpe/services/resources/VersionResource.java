package io.searchpe.services.resources;

import io.searchpe.models.VersionModel;
import io.searchpe.models.VersionProvider;
import io.searchpe.models.utils.ModelToRepresentation;
import io.searchpe.representations.idm.VersionRepresentation;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.Optional;

@Path("/versions")
public class VersionResource {

    @Inject
    VersionProvider versionProvider;

    @GET
    @Produces("application/json")
    public VersionRepresentation getVersionById() {
        Optional<VersionModel> activeVersion = versionProvider.getActiveVersion();
        VersionModel version = activeVersion.orElseThrow(() -> new NotFoundException("Not active version found"));
        return ModelToRepresentation.toRepresentation(version);
    }

}
