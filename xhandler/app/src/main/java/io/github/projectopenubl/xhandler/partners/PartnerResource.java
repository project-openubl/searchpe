package io.github.projectopenubl.xhandler.partners;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.tenancy.Actor;
import io.github.projectopenubl.xhandler.tenancy.CurrentActor;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The partner API (DESIGN.md §1.2, §14): software vendors who resell the service create and manage
 * their tenants, see their usage, and brand PDFs and emails (white-label). Partner admins act as
 * admins of their tenants (TenantAccess); partner viewers can only look.
 */
@Path("/api/v1/partners")
public class PartnerResource {

    public record PartnerView(UUID id, String name, String role) {}

    public record CreateTenant(@NotBlank @Size(max = 200) String name, @Pattern(regexp = "user|client") String ownerKind,
            @Size(max = 200) String ownerSubject) {}

    public record TenantUsage(UUID id, String name, String status, String plan, long productionDocuments, long otherDocuments) {}

    public record Branding(@Pattern(regexp = "#[0-9a-fA-F]{6}") String color, @Size(max = 300) String footer,
            @Size(max = 200) String senderName) {}

    private final CurrentActor currentActor;
    private final EntityManager em;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public PartnerResource(CurrentActor currentActor, EntityManager em, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.currentActor = currentActor;
        this.em = em;
        this.json = json;
    }

    @GET
    @Transactional
    public List<PartnerView> mine() {
        Actor actor = currentActor.get();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, name, role FROM xhandler.partners_of(:k, :s)")
                .setParameter("k", actor.kind()).setParameter("s", actor.subject()).getResultList();
        return rows.stream().map(r -> new PartnerView((UUID) r[0], (String) r[1], (String) r[2])).toList();
    }

    @POST
    @Path("/{partnerId}/tenants")
    @Transactional
    public Response createTenant(@PathParam("partnerId") UUID partnerId, @Valid CreateTenant request) {
        Actor actor = require(partnerId, "admin");
        UUID id = Ids.newId();
        em.createNativeQuery("SELECT xhandler.create_partner_tenant(:p, :id, :name, :by, :kind, :subject)")
                .setParameter("p", partnerId).setParameter("id", id).setParameter("name", request.name().strip())
                .setParameter("by", actor.auditName())
                .setParameter("kind", request.ownerSubject() == null ? null : (request.ownerKind() == null ? "user" : request.ownerKind()))
                .setParameter("subject", request.ownerSubject())
                .getSingleResult();
        return Response.status(201).entity(Map.of("id", id, "name", request.name().strip(), "partner_id", partnerId)).build();
    }

    /** The partner's tenants with their documents in a month (default: this month, Lima). */
    @GET
    @Path("/{partnerId}/tenants")
    @Transactional
    public List<TenantUsage> tenants(@PathParam("partnerId") UUID partnerId, @QueryParam("month") String month) {
        require(partnerId, "viewer");
        YearMonth period = month == null ? YearMonth.now(io.github.projectopenubl.xhandler.documents.Deadlines.LIMA) : YearMonth.parse(month);
        Instant from = period.atDay(1).atStartOfDay(io.github.projectopenubl.xhandler.documents.Deadlines.LIMA).toInstant();
        Instant to = period.plusMonths(1).atDay(1).atStartOfDay(io.github.projectopenubl.xhandler.documents.Deadlines.LIMA).toInstant();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, name, status, plan_code, production_documents, other_documents
                        FROM xhandler.partner_tenants(:p, :from, :to)""")
                .setParameter("p", partnerId).setParameter("from", from).setParameter("to", to).getResultList();
        return rows.stream().map(r -> new TenantUsage((UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                ((Number) r[4]).longValue(), ((Number) r[5]).longValue())).toList();
    }

    @PUT
    @Path("/{partnerId}/branding")
    @Transactional
    public Map<String, Object> branding(@PathParam("partnerId") UUID partnerId, @Valid Branding branding) {
        require(partnerId, "admin");
        try {
            em.createNativeQuery("UPDATE xhandler.partner SET branding = CAST(:b AS jsonb), updated_at = now() WHERE id = :p")
                    .setParameter("b", json.writeValueAsString(branding)).setParameter("p", partnerId).executeUpdate();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return Map.of("partner_id", partnerId, "branding", branding);
    }

    /** The caller must be a member of the partner with at least {@code role} (admin > viewer). Unknown partners are 404. */
    private Actor require(UUID partnerId, String role) {
        Actor actor = currentActor.get();
        String mine = mine().stream().filter(p -> p.id().equals(partnerId)).map(PartnerView::role).findFirst()
                .orElseThrow(ApiException::notFound);
        if ("admin".equals(role) && !"admin".equals(mine)) {
            throw ApiException.forbidden();
        }
        return actor;
    }

    static LocalDate today() {
        return io.github.projectopenubl.xhandler.documents.Deadlines.todayInLima();
    }
}
