package io.searchpe.services.resources;

import io.searchpe.models.VersionModel;
import io.searchpe.models.VersionProvider;
import io.searchpe.models.utils.ModelToRepresentation;
import io.searchpe.representations.idm.VersionRepresentation;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.Optional;

@Transactional
@Path("/versions")
@ApplicationScoped
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
