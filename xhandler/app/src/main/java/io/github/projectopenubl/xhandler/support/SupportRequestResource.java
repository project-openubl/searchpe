package io.github.projectopenubl.xhandler.support;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.tenancy.Actor;
import io.github.projectopenubl.xhandler.tenancy.CurrentActor;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

/** Support access, operator side: a platform operator asks a tenant for a session (DESIGN.md §26.1). */
@Path("/api/v1/support/sessions")
public class SupportRequestResource {

    public record RequestSession(@NotNull UUID tenantId, @NotBlank @Size(max = 500) String reason) {}

    private final EntityManager em;
    private final CurrentActor currentActor;

    public SupportRequestResource(EntityManager em, CurrentActor currentActor) {
        this.em = em;
        this.currentActor = currentActor;
    }

    @POST
    @Transactional
    public Response request(@Valid RequestSession request) {
        Actor actor = currentActor.get();
        if (!actor.platformSupport()) {
            throw ApiException.forbidden();
        }
        Object id = em.createNativeQuery("SELECT xhandler.request_support_session(:tenant, :operator, :reason)")
                .setParameter("tenant", request.tenantId())
                .setParameter("operator", actor.subject())
                .setParameter("reason", request.reason())
                .getSingleResult();
        if (id == null) {
            throw ApiException.notFound();
        }
        return Response.status(201).entity(Map.of("id", id)).build();
    }
}
