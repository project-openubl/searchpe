package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.jobs.UpgradeDataJob;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import org.quartz.SchedulerException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import java.util.Date;
import java.util.List;

@Transactional
@ApplicationScoped
@Path("/versions")
public class VersionResource {

    @Inject
    UpgradeDataJob upgradeDataJob;

    @GET
    @Path("/")
    @Produces("application/json")
    public List<VersionEntity> getVersions() {
        return VersionEntity.listAll();
    }

    @POST
    @Path("/")
    @Produces("application/json")
    public VersionEntity createVersion() {
        Date currentTime = new Date();

        VersionEntity version = VersionEntity.Builder.aVersionEntity()
                .withActive(false)
                .withCreatedAt(currentTime)
                .withUpdatedAt(currentTime)
                .withStatus(Status.SCHEDULED)
                .build();
        version.persist();

        try {
            upgradeDataJob.trigger(version);
        } catch (SchedulerException e) {
            throw new InternalServerErrorException(e);
        }

        return version;
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
