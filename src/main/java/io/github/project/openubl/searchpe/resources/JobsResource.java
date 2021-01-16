package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.jobs.UpgradeDataJob;
import io.github.project.openubl.searchpe.models.jpa.entity.QuartzJobDetailsEntity;
import org.quartz.SchedulerException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;

@Path("/jobs")
@Transactional
@ApplicationScoped
public class JobsResource {

    @Inject
    UpgradeDataJob upgradeDataJob;

    @GET
    @Path("/")
    @Produces("application/json")
    public List<QuartzJobDetailsEntity> getJobs() {
        return QuartzJobDetailsEntity.findAll().list();
    }

    @GET
    @Path("/triggers")
    @Produces("application/json")
    public String triggerNewJob() {
        try {
            upgradeDataJob.trigger();
        } catch (SchedulerException e) {
            throw new InternalServerErrorException(e);
        }

        return "ok";
    }

}
