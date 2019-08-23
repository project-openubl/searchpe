package io.searchpe.scheduler.resources;

import io.searchpe.scheduler.managers.SchedulerManager;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/scheduler")
@ApplicationScoped
public class SchedulerResource {

    @Inject
    SchedulerManager schedulerManager;

    @POST
    @Path("/sync")
    @Produces("application/json")
    public void startSync() {
        schedulerManager.startSync();
    }

}
