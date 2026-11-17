package io.github.projectopenubl.xhandler.jobs;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A tenant's jobs, shaped like Pulp's tasks so xhandler-ui can reuse pulp-ui-2's patterns (§10.1). */
@Path("/api/v1/tenants/{tenantId}/jobs")
public class JobResource {

    public record JobView(
            UUID id,
            String prn,
            String name,
            String state,
            int priorityClass,
            Instant createdAt,
            Instant unblockedAt,
            Instant startedAt,
            Instant finishedAt,
            String worker,
            int attempt,
            List<String> reservedResources,
            List<String> createdResources,
            Map<String, Object> result,
            Map<String, Object> error,
            String correlationId) {}

    private final TenantAccess access;
    private final Jobs jobs;

    public JobResource(TenantAccess access, Jobs jobs) {
        this.access = access;
        this.jobs = jobs;
    }

    @GET
    @Transactional
    public Page<JobView> list(
            @PathParam("tenantId") UUID tenantId,
            @QueryParam("state") String state,
            @QueryParam("name") String name,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        int size = Page.limit(limit);
        int from = Page.offset(offset);
        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> params = new java.util.HashMap<>();
        if (state != null) {
            query.append(" and state = :state");
            params.put("state", state);
        }
        if (name != null) {
            query.append(" and name = :name");
            params.put("name", name);
        }
        query.append(" order by createdAt desc");
        List<Job> page = Job.<Job>find(query.toString(), params).range(from, from + size - 1).list();
        return new Page<>(page.stream().map(JobResource::view).toList(), size, from);
    }

    @GET
    @Path("/{jobId}")
    @Transactional
    public JobView get(@PathParam("tenantId") UUID tenantId, @PathParam("jobId") UUID jobId) {
        access.enter(tenantId, Role.VIEWER);
        Job job = Job.findById(jobId);
        if (job == null) {
            throw ApiException.notFound();
        }
        return view(job);
    }

    @POST
    @Path("/{jobId}/cancel")
    @Transactional
    public JobView cancel(@PathParam("tenantId") UUID tenantId, @PathParam("jobId") UUID jobId) {
        access.enter(tenantId, Role.OPERATOR);
        Job job = Job.findById(jobId);
        if (job == null) {
            throw ApiException.notFound();
        }
        if (!"waiting".equals(job.state) && !"running".equals(job.state)) {
            throw ApiException.conflict("job_finished", "El trabajo ya terminó.");
        }
        if ("document.attempt".equals(job.name)) {
            // SUNAT attempts are controlled through the document (hold/release), never canceled
            throw ApiException.conflict("job_not_cancelable", "Los envíos a SUNAT se controlan desde el documento.");
        }
        jobs.cancel(jobId);
        return view(job);
    }

    static JobView view(Job j) {
        return new JobView(j.id, "xh:job:" + j.id, j.name, j.state, j.priorityClass, j.createdAt, j.unblockedAt, j.startedAt,
                j.finishedAt, j.worker, j.attempt, j.reservedResources, j.createdResources, j.result, j.error, j.correlationId);
    }
}
